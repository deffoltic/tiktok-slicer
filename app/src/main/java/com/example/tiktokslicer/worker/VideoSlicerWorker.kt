package com.example.tiktokslicer.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.transformer.Composition
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Effects
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

class VideoSlicerWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "slicer_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoUriStr = inputData.getString("VIDEO_URI") ?: return@withContext Result.failure()
        val sliceDurationSec = inputData.getInt("SLICE_DURATION_SEC", 30)
        val cropType = inputData.getString("CROP_TYPE") ?: "CENTER_CROP"
        val videoUri = Uri.parse(videoUriStr)

        createNotificationChannel()
        showProgressNotification(0, 100, "Starting slice process...")

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLong() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure()
        } finally {
            retriever.release()
        }

        if (durationMs <= 0L) {
            return@withContext Result.failure()
        }

        val sliceMs = sliceDurationSec * 1000L
        val totalClips = ((durationMs + sliceMs - 1) / sliceMs).toInt()

        for (i in 0 until totalClips) {
            if (isStopped) {
                return@withContext Result.failure()
            }

            val startMs = i * sliceMs
            val endMs = min(startMs + sliceMs, durationMs)

            showProgressNotification(i + 1, totalClips, "Processing clip ${i + 1} of $totalClips...")
            setProgress(workDataOf("PROGRESS" to (i + 1), "TOTAL" to totalClips))

            // Create cache file for output
            val cacheFile = File(context.cacheDir, "slice_${System.currentTimeMillis()}_$i.mp4")

            try {
                // Perform transcoding on Main Thread
                withContext(Dispatchers.Main) {
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(videoUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(startMs)
                                .setEndPositionMs(endMs)
                                .build()
                        )
                        .build()

                    val layoutMode = if (cropType == "CENTER_CROP") {
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    } else {
                        Presentation.LAYOUT_SCALE_TO_FIT
                    }

                    // 1080x1920 is standard portrait vertical TikTok resolution
                    val presentationEffect = Presentation.createForWidthAndHeight(1080, 1920, layoutMode)
                    val effects = Effects(emptyList(), listOf(presentationEffect))

                    val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                        .setEffects(effects)
                        .build()

                    transformer.executeExport(editedMediaItem, cacheFile.absolutePath)
                }

                // Copy cache file to public Movies folder via MediaStore
                saveVideoToMediaStore(cacheFile, i)
            } catch (e: Exception) {
                e.printStackTrace()
                // Continue to next clip or fail if we want to stop
            } finally {
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
            }
        }

        notificationManager.cancel(NOTIFICATION_ID)
        Result.success()
    }

    private suspend fun Transformer.executeExport(
        editedMediaItem: EditedMediaItem,
        path: String
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                removeListener(this)
                if (continuation.isActive) continuation.resume(exportResult)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                removeListener(this)
                if (continuation.isActive) continuation.resumeWithException(exportException)
            }
        }
        
        addListener(listener)
        try {
            start(editedMediaItem, path)
        } catch (e: Exception) {
            removeListener(listener)
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        continuation.invokeOnCancellation {
            removeListener(listener)
            cancel()
        }
    }

    private fun saveVideoToMediaStore(sourceFile: File, index: Int): Uri? {
        val displayName = "TikTokSlicer_clip_${System.currentTimeMillis()}_${index + 1}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/TikTokSlicer")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val resolver = context.contentResolver
        val videoUri = resolver.insert(videoCollection, values) ?: return null

        return try {
            resolver.openOutputStream(videoUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(videoUri, values, null, null)
            }
            videoUri
        } catch (e: Exception) {
            resolver.delete(videoUri, null, null)
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Video Slicing Service"
            val descriptionText = "Displays progress of movie slicing"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(current: Int, total: Int, contentText: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Slicing Movie")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setProgress(total, current, false)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
