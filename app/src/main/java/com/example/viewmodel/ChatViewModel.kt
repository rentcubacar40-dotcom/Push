package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.model.ChatGroup
import com.example.data.model.ChatMessage
import com.example.data.model.User
import com.example.data.model.UserSession
import com.example.data.repository.MoodgramRepository
import com.example.util.MediaUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val officialGroup: ChatGroup? = null,
    val directChats: List<ChatGroup> = emptyList(),
    val allUsers: List<User> = emptyList(),
    val activeChat: ChatGroup? = null,
    val activeChatResolvedAvatar: String = "",
    val resolvedAvatarMap: Map<String, String> = emptyMap(),
    val userOnlineMap: Map<String, Boolean> = emptyMap(),
    val currentUser: UserSession? = null,
    val messageInputText: String = "",
    val editingMessageId: String? = null,
    val editingMessageText: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedImageUri: Uri? = null,
    val selectedImageBytes: ByteArray? = null,
    val showNewChatDialog: Boolean = false,
    val showManageGroupDialog: Boolean = false,
    val groupEditName: String = "",
    val groupEditDescription: String = "",
    val groupEditAvatarUri: Uri? = null,
    val groupEditAvatarBytes: ByteArray? = null,
    val isUpdatingGroup: Boolean = false
)

class ChatViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        loadChats()
        startRealtimePolling()
    }

    private fun observeSession() {
        viewModelScope.launch {
            repository.sessionManager.userSessionFlow.collectLatest { session ->
                _uiState.update { it.copy(currentUser = session) }
                session?.let { repository.updateHeartbeat(it.username) }
            }
        }
    }

    private fun startRealtimePolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(3500)
                try {
                    val session = _uiState.value.currentUser
                    if (session != null) {
                        repository.updateHeartbeat(session.username)
                    }
                    val chatsDb = repository.getChatsDatabase(forceRemote = false)
                    val activeId = _uiState.value.activeChat?.id

                    val updatedActive = when (activeId) {
                        null -> null
                        AppConfig.OFFICIAL_GROUP_ID -> chatsDb.officialGroup
                        else -> chatsDb.directChats.firstOrNull { it.id == activeId }
                    }

                    _uiState.update {
                        it.copy(
                            officialGroup = chatsDb.officialGroup,
                            directChats = chatsDb.directChats,
                            activeChat = updatedActive ?: it.activeChat
                        )
                    }
                    resolveUsersAndAvatars()
                } catch (_: Exception) {}
            }
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            try {
                val chatsDb = repository.getChatsDatabase(forceRemote = false)
                _uiState.update {
                    it.copy(
                        officialGroup = chatsDb.officialGroup,
                        directChats = chatsDb.directChats,
                        isLoading = false
                    )
                }
                resolveUsersAndAvatars()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun resolveUsersAndAvatars() {
        val usersDb = try { repository.getUsersDatabase(forceRemote = false) } catch (_: Exception) { null }
        val users = usersDb?.users ?: emptyList()
        val avatarMap = mutableMapOf<String, String>()
        val onlineMap = mutableMapOf<String, Boolean>()

        users.forEach { user ->
            if (user.avatarRef.isNotEmpty()) {
                val url = repository.resolveMediaUrl(user.avatarRef)
                avatarMap[user.username] = url
                avatarMap[user.avatarRef] = url
            }
            onlineMap[user.username] = user.isOnline
        }

        val active = _uiState.value.activeChat
        var activeAvatar = ""
        if (active != null) {
            if (active.isOfficialGroup) {
                activeAvatar = if (active.avatarUrl.isNotEmpty()) repository.resolveMediaUrl(active.avatarUrl) else ""
            } else {
                val other = active.getOtherParticipant(_uiState.value.currentUser?.username ?: "")
                activeAvatar = avatarMap[other] ?: (if (active.avatarUrl.isNotEmpty()) repository.resolveMediaUrl(active.avatarUrl) else "")
            }
        }

        _uiState.update {
            it.copy(
                allUsers = users,
                resolvedAvatarMap = avatarMap,
                userOnlineMap = onlineMap,
                activeChatResolvedAvatar = activeAvatar
            )
        }
    }

    fun openChat(chatId: String) {
        viewModelScope.launch {
            val chatsDb = repository.getChatsDatabase(forceRemote = false)
            val chat = if (chatId == AppConfig.OFFICIAL_GROUP_ID) {
                chatsDb.officialGroup
            } else {
                chatsDb.directChats.firstOrNull { it.id == chatId }
            }

            var activeAvatar = ""
            if (chat != null) {
                if (chat.isOfficialGroup) {
                    activeAvatar = if (chat.avatarUrl.isNotEmpty()) repository.resolveMediaUrl(chat.avatarUrl) else ""
                } else {
                    val other = chat.getOtherParticipant(_uiState.value.currentUser?.username ?: "")
                    activeAvatar = _uiState.value.resolvedAvatarMap[other] ?: (if (chat.avatarUrl.isNotEmpty()) repository.resolveMediaUrl(chat.avatarUrl) else "")
                }
            }

            _uiState.update {
                it.copy(
                    activeChat = chat,
                    activeChatResolvedAvatar = activeAvatar,
                    messageInputText = "",
                    editingMessageId = null,
                    selectedImageUri = null,
                    selectedImageBytes = null
                )
            }
        }
    }

    fun closeActiveChat() {
        _uiState.update {
            it.copy(
                activeChat = null,
                messageInputText = "",
                editingMessageId = null,
                selectedImageUri = null,
                selectedImageBytes = null
            )
        }
    }

    fun startDirectChat(targetUsername: String, onOpened: (String) -> Unit) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                val directChat = repository.getOrCreateDirectChat(user.username, targetUsername)
                openChat(directChat.id)
                onOpened(directChat.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "No se pudo iniciar el chat: ${e.message}") }
            }
        }
    }

    fun onMessageInputChange(text: String) {
        _uiState.update { it.copy(messageInputText = text) }
    }

    fun onImageSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(selectedImageUri = null, selectedImageBytes = null) }
            return
        }
        viewModelScope.launch {
            val bytes = MediaUtils.compressImage(
                context = context,
                uri = uri,
                maxDimension = AppConfig.MAX_IMAGE_DIMENSION,
                maxBytes = AppConfig.MAX_FILE_BYTES.toLong()
            )
            _uiState.update { it.copy(selectedImageUri = uri, selectedImageBytes = bytes) }
        }
    }

    fun removeSelectedImage() {
        _uiState.update { it.copy(selectedImageUri = null, selectedImageBytes = null) }
    }

    fun sendMessage() {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return
        val text = _uiState.value.messageInputText.trim()
        val imageBytes = _uiState.value.selectedImageBytes

        if (text.isBlank() && (imageBytes == null || imageBytes.isEmpty())) return

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                val sent = repository.sendChatMessage(
                    chatId = active.id,
                    sender = user,
                    text = text,
                    mediaBytes = imageBytes,
                    filename = "chat_img_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg"
                )

                val updatedMessages = active.messages + sent
                val updatedChat = active.copy(
                    messages = updatedMessages,
                    lastMessage = if (imageBytes != null && text.isBlank()) "📷 Imagen" else text,
                    lastMessageSender = user.displayName,
                    lastMessageTime = sent.createdAt
                )

                _uiState.update {
                    it.copy(
                        activeChat = updatedChat,
                        messageInputText = "",
                        selectedImageUri = null,
                        selectedImageBytes = null,
                        isSending = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, errorMessage = "Error al enviar mensaje: ${e.message}") }
            }
        }
    }

    fun startEditingMessage(messageId: String, text: String) {
        _uiState.update { it.copy(editingMessageId = messageId, editingMessageText = text) }
    }

    fun onEditingMessageTextChange(text: String) {
        _uiState.update { it.copy(editingMessageText = text) }
    }

    fun cancelEditingMessage() {
        _uiState.update { it.copy(editingMessageId = null, editingMessageText = "") }
    }

    fun saveEditedMessage() {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return
        val messageId = _uiState.value.editingMessageId ?: return
        val newText = _uiState.value.editingMessageText.trim()
        if (newText.isBlank()) return

        viewModelScope.launch {
            try {
                repository.editChatMessage(active.id, messageId, newText, user.username, user.isAdmin)
                val updatedMessages = active.messages.map {
                    if (it.id == messageId) it.copy(text = newText, isEdited = true, editedAt = System.currentTimeMillis()) else it
                }
                _uiState.update {
                    it.copy(
                        activeChat = active.copy(messages = updatedMessages),
                        editingMessageId = null,
                        editingMessageText = ""
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al editar mensaje: ${e.message}") }
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return

        viewModelScope.launch {
            try {
                repository.deleteChatMessage(active.id, messageId, user.username, user.isAdmin)
                val updatedMessages = active.messages.filterNot { it.id == messageId }
                _uiState.update {
                    it.copy(activeChat = active.copy(messages = updatedMessages))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar mensaje: ${e.message}") }
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return

        viewModelScope.launch {
            try {
                repository.setChatMessageReaction(active.id, messageId, user.username, emoji)
                val updatedMessages = active.messages.map { msg ->
                    if (msg.id == messageId) {
                        val current = msg.reactions[user.username]
                        val updated = msg.reactions.toMutableMap()
                        if (current == emoji) updated.remove(user.username) else updated[user.username] = emoji
                        msg.copy(reactions = updated)
                    } else msg
                }
                _uiState.update { it.copy(activeChat = active.copy(messages = updatedMessages)) }
            } catch (_: Exception) {}
        }
    }

    // Administración del Grupo Oficial
    fun openManageGroupDialog() {
        val group = _uiState.value.officialGroup ?: return
        _uiState.update {
            it.copy(
                showManageGroupDialog = true,
                groupEditName = group.name,
                groupEditDescription = group.description,
                groupEditAvatarUri = null,
                groupEditAvatarBytes = null
            )
        }
    }

    fun closeManageGroupDialog() {
        _uiState.update { it.copy(showManageGroupDialog = false) }
    }

    fun onGroupEditNameChange(name: String) {
        _uiState.update { it.copy(groupEditName = name) }
    }

    fun onGroupEditDescriptionChange(desc: String) {
        _uiState.update { it.copy(groupEditDescription = desc) }
    }

    fun onGroupAvatarSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(groupEditAvatarUri = null, groupEditAvatarBytes = null) }
            return
        }
        viewModelScope.launch {
            val bytes = MediaUtils.compressImage(
                context = context,
                uri = uri,
                maxDimension = AppConfig.MAX_AVATAR_DIMENSION,
                maxBytes = AppConfig.MAX_AVATAR_BYTES.toLong()
            )
            _uiState.update { it.copy(groupEditAvatarUri = uri, groupEditAvatarBytes = bytes) }
        }
    }

    fun saveOfficialGroupSettings() {
        val user = _uiState.value.currentUser ?: return
        if (!user.isAdmin) return

        val state = _uiState.value
        _uiState.update { it.copy(isUpdatingGroup = true) }

        viewModelScope.launch {
            try {
                repository.updateOfficialGroup(
                    newName = state.groupEditName,
                    newDescription = state.groupEditDescription,
                    newAvatarBytes = state.groupEditAvatarBytes,
                    isAdmin = user.isAdmin
                )
                loadChats()
                _uiState.update {
                    it.copy(
                        showManageGroupDialog = false,
                        isUpdatingGroup = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUpdatingGroup = false, errorMessage = "Error al actualizar grupo: ${e.message}")
                }
            }
        }
    }

    fun openNewChatDialog() {
        _uiState.update { it.copy(showNewChatDialog = true) }
    }

    fun closeNewChatDialog() {
        _uiState.update { it.copy(showNewChatDialog = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
