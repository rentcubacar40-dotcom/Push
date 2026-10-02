package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Paletas de Color Moodgram (compatibles con Modo Claro y Modo Oscuro)
val MoodgramTeal = Color(0xFF0D9488) // Teal primario moderno
val MoodgramTealDark = Color(0xFF042F2E)
val MoodgramCyan = Color(0xFF06B6D4)

val MoodgramViolet = Color(0xFF7C3AED)
val MoodgramVioletDark = Color(0xFF5B21B6)

val MoodgramMagenta = Color(0xFFEC4899)
val MoodgramMagentaDark = Color(0xFF831843)

val MoodgramBlue = Color(0xFF0284C7)
val MoodgramBlueDark = Color(0xFF075985)

val MoodgramEmerald = Color(0xFF059669)
val MoodgramEmeraldDark = Color(0xFF064E3B)

val MoodgramAmber = Color(0xFFEA580C)
val MoodgramAmberDark = Color(0xFF7C2D12)

val MoodgramIndigo = Color(0xFF4F46E5)
val MoodgramOrange = Color(0xFFF97316)

// Tema Oscuro Moderno (Deep Slate OLED)
val DarkBackground = Color(0xFF0A0910)
val DarkSurface = Color(0xFF141220)
val DarkSurfaceVariant = Color(0xFF1F1B30)
val DarkOnBackground = Color(0xFFF8FAFC)
val DarkOnSurface = Color(0xFFF1F5F9)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF334155)

// Tema Claro Moderno (Clean Porcelain)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOnBackground = Color(0xFF0F172A)
val LightOnSurface = Color(0xFF1E293B)
val LightOnSurfaceVariant = Color(0xFF64748B)
val LightOutline = Color(0xFFE2E8F0)

// Paletas de Gradientes para Posts de solo texto
object PostGradients {
    val PALETTES = listOf(
        "teal" to listOf(Color(0xFF0D9488), Color(0xFF06B6D4)),
        "white" to listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC)),
        "sunset" to listOf(Color(0xFFEA580C), Color(0xFFEC4899)),
        "purple" to listOf(Color(0xFF7C3AED), Color(0xFF4F46E5)),
        "midnight" to listOf(Color(0xFF1E1B4B), Color(0xFF312E81)),
        "emerald" to listOf(Color(0xFF059669), Color(0xFF10B981)),
        "candy" to listOf(Color(0xFFDB2777), Color(0xFF9333EA)),
        "dark" to listOf(Color(0xFF18181B), Color(0xFF27272A))
    )

    fun getGradient(name: String): List<Color> {
        return PALETTES.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
            ?: listOf(Color(0xFF0D9488), Color(0xFF06B6D4))
    }

    fun isLight(name: String): Boolean = name.equals("white", ignoreCase = true)

    fun getTextColor(name: String): Color {
        return if (isLight(name)) Color(0xFF1E293B) else Color.White
    }
}
