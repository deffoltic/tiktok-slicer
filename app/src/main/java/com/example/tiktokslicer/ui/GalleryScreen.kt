package com.example.tiktokslicer.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.tiktokslicer.theme.TikTokCyan
import com.example.tiktokslicer.theme.TikTokRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ClipItem(val uri: Uri, val name: String, val durationStr: String)

@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var clips by remember { mutableStateOf<List<ClipItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var activePlayingUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val loadClips = suspend {
        isLoading = true
        val list = withContext(Dispatchers.IO) {
            queryClipsFromMediaStore(context)
        }
        clips = list
        isLoading = false
    }

    LaunchedEffect(Unit) {
        loadClips()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MY TIKTOKS",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TikTokCyan,
                    letterSpacing = 2.sp
                )

                IconButton(
                    onClick = {
                        scope.launch { loadClips() }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Gallery",
                        tint = TikTokCyan
                    )
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TikTokCyan)
                }
            } else if (clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sliced clips found.\nStart slicing movies to see them here!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(clips) { clip ->
                        ClipCard(
                            clip = clip,
                            onPlay = { activePlayingUri = clip.uri },
                            onShare = { shareVideo(context, clip.uri) }
                        )
                    }
                }
            }
        }

        // Full Screen Video Player Overlay Dialog
        activePlayingUri?.let { uri ->
            VideoPlayerDialog(
                uri = uri,
                onDismiss = { activePlayingUri = null }
            )
        }
    }
}

@Composable
fun ClipCard(
    clip: ClipItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f) // TikTok Vertical Aspect Ratio!
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Placeholder/Dark Background representation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Play Icon in the Center
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Video",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Info and share footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = clip.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = clip.durationStr,
                        color = TikTokCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { onShare() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = TikTokRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Initialize ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }

    // Release player when dismissed
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ExoPlayer AndroidView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            // Close button overlay
            IconButton(
                onClick = { onDismiss() },
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Player",
                    tint = Color.White
                )
            }
        }
    }
}

private fun queryClipsFromMediaStore(context: Context): List<ClipItem> {
    val clips = mutableListOf<ClipItem>()
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )

    val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
    } else {
        "${MediaStore.Video.Media.DATA} LIKE ?"
    }

    val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf("%Movies/TikTokSlicer%")
    } else {
        arrayOf("%/TikTokSlicer/%")
    }

    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

    try {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val durationMs = cursor.getLong(durationCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                // Format duration
                val totalSec = durationMs / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                val durationStr = String.format("%02d:%02d", min, sec)

                clips.add(ClipItem(uri, name, durationStr))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return clips
}

private fun shareVideo(context: Context, uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share TikTok Clip"))
}
