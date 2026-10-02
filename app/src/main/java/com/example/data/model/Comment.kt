package com.example.data.model

data class Comment(
    val id: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarRef: String = "",
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editedAt: Long = 0L
)
