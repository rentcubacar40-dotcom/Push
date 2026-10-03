package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.util.MediaUtils
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showControls: Boolean = true,
    aspectRatio: Float = 0f,
    initiallyMuted: Boolean = false
) {
    val context = LocalContext.current
    var isActivated by remember(videoUrl) { mutableStateOf(autoPlay) }
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isMuted by remember { mutableStateOf(initiallyMuted) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var showOverlayControls by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }

    if (!isActivated) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(if (aspectRatio > 0f) Modifier.aspectRatio(aspectRatio) else Modifier)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                    )
                )
                .clickable {
                    isActivated = true
                    isPlaying = true
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                shadowElevation = 6.dp,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Reproducir video",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
        return
    }

    val exoPlayer = remember(videoUrl, isActivated) {
        if (!isActivated || videoUrl.isBlank()) null
        else {
            try {
                isLoading = true
                val renderersFactory = DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)

                ExoPlayer.Builder(context, renderersFactory).build().apply {
                    val mediaItem = MediaItem.fromUri(videoUrl)
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ALL
                    playWhenReady = true
                    volume = if (isMuted) 0f else 1f
                    prepare()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) {
            isLoading = false
            hasError = true
            onDispose {}
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            isLoading = true
                            hasError = false
                        }
                        Player.STATE_READY -> {
                            isLoading = false
                            hasError = false
                            durationMs = exoPlayer.duration.coerceAtLeast(0L)
                        }
                        Player.STATE_ENDED -> {
                            isLoading = false
                        }
                        Player.STATE_IDLE -> {
                            isLoading = false
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    isLoading = false
                    hasError = true
                }
            }
            exoPlayer.addListener(listener)

            onDispose {
                try {
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                } catch (_: Exception) {}
            }
        }
    }

    // Actualización periódica del progreso de reproducción
    LaunchedEffect(isPlaying, exoPlayer) {
        while (isPlaying && exoPlayer != null) {
            try {
                currentPositionMs = exoPlayer.currentPosition
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                if (durationMs > 0) {
                    progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            } catch (_: Exception) {}
            delay(250)
        }
    }

    // Auto-ocultar controles tras 3 segundos
    LaunchedEffect(showOverlayControls) {
        if (showOverlayControls) {
            delay(3000)
            showOverlayControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (aspectRatio > 0f) Modifier.aspectRatio(aspectRatio) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .clickable {
                showOverlayControls = !showOverlayControls
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Indicador de carga
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            }
        }

        // Estado de error con reintento
        if (hasError) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Error al reproducir video",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            hasError = false
                            isLoading = true
                            exoPlayer?.prepare()
                            exoPlayer?.play()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reintentar",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Controles superpuestos con animación
        AnimatedVisibility(
            visible = showOverlayControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            ) {
                // Botón central de Play / Pause
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clickable {
                            exoPlayer?.let { player ->
                                if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Barra inferior de progreso y duración
                if (showControls) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = MediaUtils.formatDuration(currentPositionMs),
                            fontSize = 11.sp,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Barra de progreso personalizada
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.35f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .size(width = 0.dp, height = 4.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color(0xFF06B6D4), Color(0xFF10B981))
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = MediaUtils.formatDuration(durationMs),
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Botón de silenciar / audio SIEMPRE VISIBLE en la esquina superior derecha
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.75f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(38.dp)
                .clickable {
                    isMuted = !isMuted
                    exoPlayer?.volume = if (isMuted) 0f else 1f
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
