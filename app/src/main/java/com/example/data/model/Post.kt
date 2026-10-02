package com.example.data.model

data class Post(
    val id: String,
    val authorUsername: String,
    val authorDisplayName: String,
    val authorAvatarRef: String = "",
    val text: String = "",
    val mediaUrl: String = "", // URL completa o relativa
    val mediaType: String = "image", // "image", "video", o "text"
    val backgroundColor: String = "", // Para posts de solo texto con gradiente ("sunset", "teal", "purple", "midnight", "emerald", "coral")
    val fileRef: String = "", // Nombre de archivo o ref en la nube
    val fileSize: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val likes: List<String> = emptyList(), // Lista de usernames que dieron like
    val reactions: Map<String, String> = emptyMap(), // username -> emoji ("❤️", "🔥", "😂", "😮", "😢", "👏")
    val comments: List<Comment> = emptyList()
) {
    val isVideo: Boolean
        get() = mediaType.equals("video", ignoreCase = true)

    val isTextOnly: Boolean
        get() = mediaUrl.isEmpty() || mediaType.equals("text", ignoreCase = true)

    val allReactions: Map<String, String>
        get() {
            if (reactions.isNotEmpty()) return reactions
            // Fallback para posts antiguos con lista simple de likes
            return likes.associateWith { "❤️" }
        }

    val totalReactionsCount: Int
        get() = allReactions.size

    fun getReactionSummary(): Map<String, Int> {
        val summary = mutableMapOf<String, Int>()
        allReactions.values.forEach { emoji ->
            summary[emoji] = (summary[emoji] ?: 0) + 1
        }
        return summary
    }

    fun getUserReaction(username: String): String? {
        return allReactions[username]
    }
}
