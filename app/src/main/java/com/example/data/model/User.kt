package com.example.data.model

data class User(
    val username: String, // Formato @nombre (único, case-insensitive)
    val displayName: String,
    val passwordHash: String, // SHA-256(password + salt)
    val salt: String,
    val avatarRef: String = "", // Referencia del archivo en Moodle
    val role: String = "user", // "admin" o "user"
    val createdAt: Long = System.currentTimeMillis(),
    val isBanned: Boolean = false
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true) || username.equals("@Eliel_21", ignoreCase = true)
}

data class UserSession(
    val username: String,
    val displayName: String,
    val role: String,
    val avatarRef: String
) {
    val isAdmin: Boolean
        get() = role.equals("admin", ignoreCase = true) || username.equals("@Eliel_21", ignoreCase = true)
}
