package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    showRing: Boolean = false
) {
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val colorIndex = abs(displayName.hashCode()) % fallbackColors.size
    val fallbackColor = fallbackColors[colorIndex]

    val ringModifier = if (showRing) {
        modifier
            .size(size)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(MoodgramViolet, MoodgramMagenta, MoodgramIndigo)),
                shape = CircleShape
            )
            .clip(CircleShape)
    } else {
        modifier
            .size(size)
            .clip(CircleShape)
    }

    val token = "ddd9b89ebd8115d4a9c1eaae298afde9"
    val cleanUrl = remember(avatarUrl) {
        if (avatarUrl.isNullOrBlank()) {
            null
        } else if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            var url = avatarUrl
            if (url.contains("/pluginfile.php/1/")) {
                url = url.replace("/pluginfile.php/1/", "/pluginfile.php/${com.example.config.AppConfig.DEFAULT_CONTEXT_ID}/")
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
            // Filename format
            val clean = avatarUrl.trimStart('/')
            if (clean.contains("Eliel_21")) {
                "https://cursos.ucf.edu.cu/webservice/pluginfile.php/${com.example.config.AppConfig.DEFAULT_CONTEXT_ID}/core_competency/userevidence/852/$clean?token=$token"
            } else {
                "https://cursos.ucf.edu.cu/webservice/pluginfile.php/${com.example.config.AppConfig.DEFAULT_CONTEXT_ID}/user/private/$clean?token=$token"
            }
        }
    }

    var hasError by androidx.compose.runtime.remember(cleanUrl) {
        androidx.compose.runtime.mutableStateOf(false)
    }

    Box(
        modifier = ringModifier,
        contentAlignment = Alignment.Center
    ) {
        if (!cleanUrl.isNullOrBlank() && !hasError) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cleanUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar de $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                onError = { hasError = true }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(fallbackColor),
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
