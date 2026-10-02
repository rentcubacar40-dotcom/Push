package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.MediaUtils
import kotlinx.coroutines.delay

@Composable
fun AudioNotePlayerView(
    audioUrl: String,
    durationMs: Long,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf(durationMs.toInt()) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUrl) {
        val player = MediaPlayer().apply {
            try {
                setDataSource(audioUrl)
                setOnPreparedListener {
                    isPrepared = true
                    totalDurationMs = duration.coerceAtLeast(durationMs.toInt())
                }
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0
                }
                prepareAsync()
            } catch (_: Exception) {
                isPrepared = false
            }
        }
        mediaPlayer = player

        onDispose {
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            try {
                currentPositionMs = mediaPlayer?.currentPosition ?: 0
            } catch (_: Exception) {}
            delay(200)
        }
    }

    val progress = if (totalDurationMs > 0) {
        (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val accentColor = if (isMe) Color.White else MaterialTheme.colorScheme.primary
    val trackBgColor = if (isMe) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Botón Play / Pause
        Surface(
            shape = CircleShape,
            color = if (isMe) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(40.dp)
                .clickable(enabled = isPrepared) {
                    val player = mediaPlayer ?: return@clickable
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.start()
                        isPlaying = true
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!isPrepared) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Ondas simuladas / Barra de progreso estilo WhatsApp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barHeights = listOf(6, 12, 18, 10, 16, 8, 14, 18, 12, 6, 15, 10, 16, 8, 12, 18, 14, 8, 12, 6)
                barHeights.forEachIndexed { index, h ->
                    val barFraction = (index + 1).toFloat() / barHeights.size.toFloat()
                    val isPast = progress >= barFraction
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isPast) accentColor else trackBgColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = MediaUtils.formatDuration(currentPositionMs.toLong()),
                    fontSize = 11.sp,
                    color = if (isMe) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = MediaUtils.formatDuration(totalDurationMs.toLong()),
                    fontSize = 11.sp,
                    color = if (isMe) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
