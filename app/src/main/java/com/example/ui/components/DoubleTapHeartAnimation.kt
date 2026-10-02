package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MoodgramMagenta

@Composable
fun DoubleTapHeartAnimation(
    visible: Boolean,
    onAnimationEnd: () -> Unit
) {
    if (!visible) return

    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(visible) {
        scale.snapTo(0f)
        alpha.snapTo(1f)
        // Zoom-in pop
        scale.animateTo(
            targetValue = 1.3f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
        )
        // Settle down slightly
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 100)
        )
        // Fade out
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 200)
        )
        onAnimationEnd()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Corazón",
            tint = MoodgramMagenta.copy(alpha = alpha.value),
            modifier = Modifier
                .size(100.dp)
                .scale(scale.value)
        )
    }
}
