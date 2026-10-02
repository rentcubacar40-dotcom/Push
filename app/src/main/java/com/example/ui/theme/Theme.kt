package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getThemeColorScheme(darkTheme: Boolean, colorTheme: String): ColorScheme {
    val (primary, primaryDark, secondary) = when (colorTheme.uppercase()) {
        "VIOLET" -> Triple(MoodgramViolet, MoodgramVioletDark, MoodgramMagenta)
        "MAGENTA" -> Triple(MoodgramMagenta, MoodgramMagentaDark, MoodgramIndigo)
        "BLUE" -> Triple(MoodgramBlue, MoodgramBlueDark, MoodgramCyan)
        "EMERALD" -> Triple(MoodgramEmerald, MoodgramEmeraldDark, MoodgramTeal)
        "AMBER" -> Triple(MoodgramAmber, MoodgramAmberDark, MoodgramOrange)
        else -> Triple(MoodgramTeal, MoodgramTealDark, MoodgramCyan) // Default TEAL
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryDark,
            onPrimaryContainer = Color(0xFFCCFBF1),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF1E293B),
            onSecondaryContainer = Color(0xFFF1F5F9),
            tertiary = MoodgramOrange,
            onTertiary = Color.White,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = LightSurfaceVariant,
            onPrimaryContainer = primaryDark,
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF1F5F9),
            onSecondaryContainer = Color(0xFF0F172A),
            tertiary = MoodgramOrange,
            onTertiary = Color.White,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: String = "TEAL",
    content: @Composable () -> Unit
) {
    val colorScheme = getThemeColorScheme(darkTheme, colorTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
