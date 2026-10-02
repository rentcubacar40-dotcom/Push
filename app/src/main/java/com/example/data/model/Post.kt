package com.example.data.model

data class Post(
    val id: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarRef: String = "",
    val text: String = "",
    val mediaUrl: String = "", // URL completa o relativa
    val mediaType: String = "image", // "image" o "video"
    val fileRef: String = "", // Nombre de archivo o ref de Moodle
    val fileSize: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val likes: List<String> = emptyList(), // Lista de usernames que dieron like
    val comments: List<Comment> = emptyList()
) {
    val isVideo: Boolean
        get() = mediaType.equals("video", ignoreCase = true)
}
