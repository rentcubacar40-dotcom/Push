package com.example.data.model

data class ChatMessage(
    val id: String,
    val chatId: String,
    val senderUsername: String,
    val senderDisplayName: String,
    val senderAvatarRef: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "", // "image"
    val createdAt: Long = System.currentTimeMillis(),
    val isEdited: Boolean = false,
    val editedAt: Long = 0L,
    val reactions: Map<String, String> = emptyMap() // username -> emoji
)

data class ChatGroup(
    val id: String,
    val name: String,
    val avatarUrl: String = "",
    val description: String = "",
    val isOfficialGroup: Boolean = false,
    val members: List<String> = emptyList(), // Lista de usernames
    val lastMessage: String? = null,
    val lastMessageSender: String? = null,
    val lastMessageTime: Long? = null,
    val messages: List<ChatMessage> = emptyList()
) {
    fun getOtherParticipant(currentUsername: String): String {
        return members.firstOrNull { !it.equals(currentUsername, ignoreCase = true) } ?: currentUsername
    }
}

data class ChatsDatabase(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val officialGroup: ChatGroup = ChatGroup(
        id = "official_group",
        name = "Grupo Oficial Moodgram",
        description = "Bienvenidos a la comunidad oficial de Moodgram. Comparte, conversa y disfruta con todos los usuarios.",
        isOfficialGroup = true,
        members = emptyList(),
        messages = emptyList()
    ),
    val directChats: List<ChatGroup> = emptyList()
)
