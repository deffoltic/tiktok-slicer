package com.example.tiktokslicer.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import com.example.tiktokslicer.theme.TikTokCyan
import com.example.tiktokslicer.theme.TikTokRed
import com.example.tiktokslicer.worker.VideoSlicerWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class VideoInfo(val name: String, val durationMs: Long)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoInfo by remember { mutableStateOf<VideoInfo?>(null) }
    var sliceDurationSec by remember { mutableStateOf(30) }
    var cropType by remember { mutableStateOf("CENTER_CROP") } // "CENTER_CROP" or "FIT"
    var isQueryingMetadata by remember { mutableStateOf(false) }

    // Observe WorkManager progress
    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow("video_slicing")
        .collectAsState(initial = emptyList())

    val activeWorkInfo = workInfos.firstOrNull()
    val isProcessing = activeWorkInfo?.state == WorkInfo.State.RUNNING ||
            activeWorkInfo?.state == WorkInfo.State.ENQUEUED

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            videoInfo = null
            isQueryingMetadata = true
        }
    }

    // Load video info asynchronously
    LaunchedEffect(selectedVideoUri) {
        val uri = selectedVideoUri ?: return@LaunchedEffect
        isQueryingMetadata = true
        val info = withContext(Dispatchers.IO) {
            getVideoInfo(context, uri)
        }
        videoInfo = info
        isQueryingMetadata = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title/Branding
        Text(
            text = "TIKTOK SLICER",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Video Selection Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 2.dp,
                    brush = Brush.horizontalGradient(listOf(TikTokRed, TikTokCyan)),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(enabled = !isProcessing) {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isQueryingMetadata) {
                CircularProgressIndicator(color = TikTokCyan)
            } else if (selectedVideoUri == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Select Video",
                        tint = TikTokCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Pick Landscape Movie",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "MP4, MKV, etc.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video Loaded",
                        tint = TikTokRed,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = videoInfo?.name ?: "Loaded Video",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    videoInfo?.let {
                        Text(
                            text = "Duration: ${formatDuration(it.durationMs)}",
                            fontSize = 13.sp,
                            color = TikTokCyan
                        )
                    }
                }
            }
        }

        // Settings Panel
        AnimatedVisibility(
            visible = selectedVideoUri != null && !isProcessing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Slicing Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Target Duration Setting
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Clip Duration",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${sliceDurationSec}s",
                                fontSize = 14.sp,
                                color = TikTokCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = sliceDurationSec.toFloat(),
                            onValueChange = { sliceDurationSec = it.toInt() },
                            valueRange = 10f..60f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = TikTokCyan,
                                activeTrackColor = TikTokCyan
                            )
                        )
                    }

                    // Crop Selection Setting
                    Column {
                        Text(
                            text = "Aspect Ratio Crop Style",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { cropType = "CENTER_CROP" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cropType == "CENTER_CROP") TikTokRed else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Center Crop 9:16", color = Color.White, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { cropType = "FIT" },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (cropType == "FIT") TikTokCyan else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Fit (Letterbox)", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Processing / Job Progress Screen
        AnimatedVisibility(
            visible = isProcessing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val progressData = activeWorkInfo?.progress
                    val currentProgress = progressData?.getInt("PROGRESS", 0) ?: 0
                    val totalClips = progressData?.getInt("TOTAL", 1) ?: 1

                    Text(
                        text = "Slicing Movie in Background...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (totalClips > 1) {
                        val progressPercent = (currentProgress.toFloat() / totalClips.toFloat())
                        LinearProgressIndicator(
                            progress = progressPercent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = TikTokCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = "Exported $currentProgress of $totalClips clips",
                            fontSize = 13.sp,
                            color = TikTokCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = TikTokRed
                        )
                    }

                    Button(
                        onClick = {
                            WorkManager.getInstance(context).cancelUniqueWork("video_slicing")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel Job", color = Color.White)
                    }
                }
            }
        }

        // Action Trigger Button
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val uri = selectedVideoUri ?: return@Button
                val request = OneTimeWorkRequestBuilder<VideoSlicerWorker>()
                    .setInputData(
                        workDataOf(
                            "VIDEO_URI" to uri.toString(),
                            "SLICE_DURATION_SEC" to sliceDurationSec,
                            "CROP_TYPE" to cropType
                        )
                    )
                    .addTag("slicer_job")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "video_slicing",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            },
            enabled = selectedVideoUri != null && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = TikTokRed,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = if (isProcessing) "PROCESSING..." else "SLICE MOVIE TO TIKTOKS",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

private fun getVideoInfo(context: Context, uri: Uri): VideoInfo? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val duration = durationStr?.toLong() ?: 0L

        var name = "Selected Video"
        val projection = arrayOf(MediaStore.Video.Media.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        VideoInfo(name, duration)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        retriever.release()
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
