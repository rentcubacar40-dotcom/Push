package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.config.AppConfig
import com.example.ui.theme.MoodgramIndigo
import com.example.ui.theme.MoodgramMagenta
import com.example.ui.theme.MoodgramViolet
import kotlin.math.abs

@Composable
fun AvatarImage(
    avatarUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    showRing: Boolean = false,
    showOnlineIndicator: Boolean = false,
    isOnline: Boolean = false
) {
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val colorIndex = abs(displayName.hashCode()) % fallbackColors.size
    val fallbackColor = fallbackColors[colorIndex]

    val token = "ddd9b89ebd8115d4a9c1eaae298afde9"
    val cleanUrl = remember(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) {
            null
        } else {
            var url = avatarUrl.replace("\\u003d", "=").replace("&amp;", "&").trim()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                if (url.contains("/pluginfile.php/1/")) {
                    url = url.replace("/pluginfile.php/1/", "/pluginfile.php/${AppConfig.DEFAULT_CONTEXT_ID}/")
                }
                if (url.contains("/pluginfile.php/") && !url.contains("/webservice/pluginfile.php/")) {
                    url = url.replace("/pluginfile.php/", "/webservice/pluginfile.php/")
                }
                if (!url.contains("token=")) {
                    val sep = if (url.contains("?")) "&" else "?"
                    "$url${sep}token=$token"
                } else {
                    url
                }
            } else {
                val clean = url.trimStart('/')
                "${AppConfig.MOODLE_URL}webservice/pluginfile.php/${AppConfig.DEFAULT_CONTEXT_ID}/core_competency/userevidence/852/$clean?token=$token"
            }
        }
    }

    val ringModifier = if (showRing) {
        Modifier
            .size(size)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(MoodgramViolet, MoodgramMagenta, MoodgramIndigo)),
                shape = CircleShape
            )
            .clip(CircleShape)
    } else {
        Modifier
            .size(size)
            .clip(CircleShape)
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = ringModifier,
            contentAlignment = Alignment.Center
        ) {
            if (!cleanUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(cleanUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Avatar de $displayName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                    loading = {
                        InitialsPlaceholder(initial = initial, size = size, backgroundColor = fallbackColor)
                    },
                    error = {
                        InitialsPlaceholder(initial = initial, size = size, backgroundColor = fallbackColor)
                    }
                )
            } else {
                InitialsPlaceholder(initial = initial, size = size, backgroundColor = fallbackColor)
            }
        }

        // Indicador de estado en línea
        if (showOnlineIndicator) {
            val dotSize = (size.value * 0.28f).coerceIn(8f, 16f).dp
            val dotColor = if (isOnline) Color(0xFF10B981) else Color(0xFF9CA3AF)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .size(dotSize)
                    .background(Color.White, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
                    .clip(CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(dotColor, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun InitialsPlaceholder(
    initial: String,
    size: Dp,
    backgroundColor: Color
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp
        )
    }
}

private val fallbackColors = listOf(
    Color(0xFF7C3AED),
    Color(0xFFEC4899),
    Color(0xFF4F46E5),
    Color(0xFF06B6D4),
    Color(0xFF10B981),
    Color(0xFFF97316),
    Color(0xFF8B5CF6)
)
