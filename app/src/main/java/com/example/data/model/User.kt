package com.example.data.model

data class User(
    val username: String, // Formato @nombre (único, case-insensitive)
    val displayName: String,
    val passwordHash: String, // SHA-256(password + salt)
    val salt: String,
    val avatarRef: String = "", // Referencia del archivo en la nube
    val role: String = "user", // "admin" o "user"
    val createdAt: Long = System.currentTimeMillis(),
    val isBanned: Boolean = false,
    val lastActive: Long = 0L
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true) || username.equals("@Eliel_21", ignoreCase = true)

    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastActive) < 90_000L // 1.5 minutos

    fun getLastSeenText(): String {
        if (isOnline) return "En línea"
        if (lastActive <= 0L) return "Desconectado"
        val diff = System.currentTimeMillis() - lastActive
        val minutes = diff / (60 * 1000)
        return when {
            minutes < 1 -> "Hace un momento"
            minutes < 60 -> "Hace $minutes min"
            minutes < 1440 -> "Hace ${minutes / 60} h"
            else -> "Hace ${minutes / 1440} d"
        }
    }
}

data class UserSession(
    val username: String,
    val displayName: String,
    val role: String,
    val avatarRef: String,
    val lastActive: Long = 0L
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true) || username.equals("@Eliel_21", ignoreCase = true)

    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastActive) < 90_000L
}
