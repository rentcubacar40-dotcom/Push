package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SessionManager
import com.example.data.repository.MoodgramRepository
import com.example.ui.theme.MoodgramCyan
import com.example.ui.theme.MoodgramTeal
import com.example.ui.theme.MoodgramTealDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull

@Composable
fun SplashScreen(
    repository: MoodgramRepository,
    sessionManager: SessionManager,
    onNavigateToFeed: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val scale = remember { Animatable(0.7f) }
    val glow = remember { Animatable(0.95f) }
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        // Animación suave de entrada
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        // Pulsación suave de resplandor
        glow.animateTo(
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    LaunchedEffect(Unit) {
        // Asegurar que el admin esté listo y verificar sesión
        try {
            repository.ensureAdminSeeded()
        } catch (_: Exception) {}

        delay(1200)
        val session = sessionManager.userSessionFlow.firstOrNull()
        if (session != null) {
            onNavigateToFeed()
        } else {
            onNavigateToAuth()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        if (isDark) MoodgramTealDark.copy(alpha = 0.45f) else Color(0xFFCCFBF1).copy(alpha = 0.5f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono de logo con gradiente Teal y resplandor
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(scale.value * glow.value)
                    .shadow(elevation = 20.dp, shape = CircleShape, spotColor = MoodgramTeal)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF14B8A6), MoodgramTeal, MoodgramTealDark)
                        ),
                        shape = CircleShape
                    )
                    .border(2.5.dp, Color(0xFF5EEAD4).copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Moodgram Logo",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "Moodgram",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MoodgramTeal,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tu vida en fotos y videos",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(36.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = MoodgramTeal,
                trackColor = MoodgramTeal.copy(alpha = 0.2f),
                strokeWidth = 2.8.dp
            )
        }
    }
}
