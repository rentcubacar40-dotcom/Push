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
import com.example.util.VoiceNoteManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class SendingFileStatus(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val progress: Float = 0f,
    val isUploading: Boolean = true,
    val error: String? = null
)

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
    val replyingToMessage: ChatMessage? = null,
    val showReadInfoForMessage: ChatMessage? = null,
    val editingMessageId: String? = null,
    val editingMessageText: String = "",
    val isSending: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedMediaUri: Uri? = null,
    val selectedMediaType: String = "", // "image", "video", "audio", "document"
    val selectedMediaBytes: ByteArray? = null,
    val selectedMediaDurationMs: Long = 0L,
    val selectedMediaFileName: String = "",
    val isRecordingVoice: Boolean = false,
    val voiceRecordDurationMs: Long = 0L,
    val voiceRecordAmplitude: Int = 0,
    val showNewChatDialog: Boolean = false,
    val showManageGroupDialog: Boolean = false,
    val groupEditName: String = "",
    val groupEditDescription: String = "",
    val groupEditAvatarUri: Uri? = null,
    val groupEditAvatarBytes: ByteArray? = null,
    val isUpdatingGroup: Boolean = false,
    val sendingFiles: List<SendingFileStatus> = emptyList()
) {
    val selectedImageUri: Uri? get() = if (selectedMediaType == "image") selectedMediaUri else null
}

class ChatViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState(isLoading = true))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var voiceNoteManager: VoiceNoteManager? = null

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

    private var currentChatIdToOpen: String? = null

    private fun startRealtimePolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(4500)
                try {
                    val session = _uiState.value.currentUser
                    if (session != null) {
                        repository.updateHeartbeat(session.username)
                    }
                    val chatsDb = repository.getChatsDatabase(forceRemote = false)
                    val targetId = currentChatIdToOpen ?: _uiState.value.activeChat?.id

                    val updatedActive = when (targetId) {
                        null -> null
                        AppConfig.OFFICIAL_GROUP_ID -> chatsDb.officialGroup
                        else -> chatsDb.directChats.firstOrNull { it.id == targetId }
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
                val targetId = currentChatIdToOpen ?: _uiState.value.activeChat?.id
                val active = when (targetId) {
                    null -> null
                    AppConfig.OFFICIAL_GROUP_ID -> chatsDb.officialGroup
                    else -> chatsDb.directChats.firstOrNull { it.id == targetId }
                }

                _uiState.update {
                    it.copy(
                        officialGroup = chatsDb.officialGroup,
                        directChats = chatsDb.directChats,
                        activeChat = active ?: it.activeChat,
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
                val otherUsername = active.getOtherParticipant(_uiState.value.currentUser?.username ?: "")
                val otherUser = users.firstOrNull { it.username.equals(otherUsername, ignoreCase = true) }
                if (otherUser?.avatarRef?.isNotEmpty() == true) {
                    activeAvatar = repository.resolveMediaUrl(otherUser.avatarRef)
                }
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
        currentChatIdToOpen = chatId
        viewModelScope.launch {
            try {
                // 1. Cargar base de datos local
                val chatsDb = repository.getChatsDatabase(forceRemote = false)
                var active = if (chatId == AppConfig.OFFICIAL_GROUP_ID) {
                    chatsDb.officialGroup
                } else {
                    chatsDb.directChats.firstOrNull { it.id == chatId || it.members.contains(chatId) }
                }

                // 2. Si no se encontró en local, buscar en remoto o auto-crear si es un usuario
                if (active == null && chatId != AppConfig.OFFICIAL_GROUP_ID) {
                    val remoteDb = repository.getChatsDatabase(forceRemote = true)
                    active = remoteDb.directChats.firstOrNull { it.id == chatId || it.members.contains(chatId) }

                    val currentUser = _uiState.value.currentUser
                    if (active == null && currentUser != null && chatId.isNotBlank()) {
                        val otherUsername = if (chatId.startsWith("dm_")) {
                            chatId.removePrefix("dm_").split("_").firstOrNull {
                                !it.equals(currentUser.username.lowercase(), ignoreCase = true)
                            } ?: chatId
                        } else chatId
                        try {
                            active = repository.getOrCreateDirectChat(currentUser.username, otherUsername)
                        } catch (_: Exception) {}
                    }
                }

                _uiState.update {
                    it.copy(
                        officialGroup = chatsDb.officialGroup,
                        directChats = chatsDb.directChats,
                        activeChat = active ?: it.activeChat,
                        messageInputText = "",
                        replyingToMessage = null,
                        selectedMediaUri = null,
                        selectedMediaBytes = null,
                        selectedMediaType = "",
                        isLoading = false
                    )
                }
                resolveUsersAndAvatars()

                // 3. Marcar mensajes como leídos
                val user = _uiState.value.currentUser
                if (user != null) {
                    try {
                        repository.markMessagesAsRead(chatId, user.username)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    /**
     * Envía múltiples archivos o documentos seleccionados del almacenamiento,
     * mostrando el progreso individual de cada archivo.
     */
    fun sendMultipleFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return

        val initialStatuses = uris.map { uri ->
            SendingFileStatus(
                uri = uri,
                name = MediaUtils.getFileName(context, uri),
                sizeBytes = MediaUtils.getFileSize(context, uri),
                progress = 0.05f
            )
        }

        _uiState.update { it.copy(sendingFiles = it.sendingFiles + initialStatuses) }

        viewModelScope.launch {
            for (status in initialStatuses) {
                try {
                    // Actualizar progreso a leyendo / preparando
                    _uiState.update { state ->
                        state.copy(sendingFiles = state.sendingFiles.map {
                            if (it.uri == status.uri) it.copy(progress = 0.25f) else it
                        })
                    }

                    val mime = MediaUtils.getMimeType(context, status.uri)
                    val rawBytes = MediaUtils.readBytes(context, status.uri)
                    if (rawBytes == null || rawBytes.isEmpty()) {
                        throw Exception("No se pudo leer el archivo")
                    }

                    // Si es imagen grande, comprimirla
                    val finalBytes = if (mime.startsWith("image")) {
                        MediaUtils.compressImage(context, status.uri) ?: rawBytes
                    } else {
                        rawBytes
                    }

                    if (finalBytes.size > AppConfig.MAX_FILE_BYTES) {
                        throw Exception("El archivo ${status.name} supera el límite de 4 MB")
                    }

                    // Progreso a subiendo
                    _uiState.update { state ->
                        state.copy(sendingFiles = state.sendingFiles.map {
                            if (it.uri == status.uri) it.copy(progress = 0.65f) else it
                        })
                    }

                    val mediaType = when {
                        mime.startsWith("image") -> "image"
                        mime.startsWith("video") -> "video"
                        mime.startsWith("audio") -> "audio"
                        else -> "document"
                    }

                    repository.sendChatMessage(
                        chatId = active.id,
                        sender = user,
                        text = "",
                        mediaBytes = finalBytes,
                        filename = status.name,
                        mimeType = mime,
                        mediaType = mediaType
                    )

                    // Progreso completado
                    _uiState.update { state ->
                        state.copy(sendingFiles = state.sendingFiles.map {
                            if (it.uri == status.uri) it.copy(progress = 1.0f) else it
                        })
                    }
                    delay(300)

                    // Quitar de lista de envío
                    _uiState.update { state ->
                        state.copy(sendingFiles = state.sendingFiles.filterNot { it.uri == status.uri })
                    }

                    val updatedDb = repository.getChatsDatabase(forceRemote = false)
                    val updatedActive = if (active.isOfficialGroup) updatedDb.officialGroup else updatedDb.directChats.firstOrNull { it.id == active.id }
                    _uiState.update { it.copy(activeChat = updatedActive ?: it.activeChat) }

                } catch (e: Exception) {
                    _uiState.update { state ->
                        state.copy(
                            sendingFiles = state.sendingFiles.map {
                                if (it.uri == status.uri) it.copy(error = e.message, progress = 0f) else it
                            },
                            errorMessage = "Error enviando ${status.name}: ${e.message}"
                        )
                    }
                    delay(1500)
                    _uiState.update { state ->
                        state.copy(sendingFiles = state.sendingFiles.filterNot { it.uri == status.uri })
                    }
                }
            }
        }
    }

    fun setReplyingToMessage(message: ChatMessage?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun setShowReadInfoForMessage(message: ChatMessage?) {
        _uiState.update { it.copy(showReadInfoForMessage = message) }
    }

    fun onMessageInputChange(text: String) {
        _uiState.update { it.copy(messageInputText = text) }
    }

    fun onImageSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(selectedMediaUri = null, selectedMediaBytes = null, selectedMediaType = "") }
            return
        }
        viewModelScope.launch {
            val bytes = MediaUtils.compressImage(
                context = context,
                uri = uri,
                maxDimension = AppConfig.MAX_IMAGE_DIMENSION,
                maxBytes = AppConfig.MAX_FILE_BYTES.toLong()
            )
            _uiState.update {
                it.copy(
                    selectedMediaUri = uri,
                    selectedMediaBytes = bytes,
                    selectedMediaType = "image"
                )
            }
        }
    }

    fun onVideoSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(selectedMediaUri = null, selectedMediaBytes = null, selectedMediaType = "") }
            return
        }
        viewModelScope.launch {
            val size = MediaUtils.getFileSize(context, uri)
            if (size > AppConfig.MAX_FILE_BYTES) {
                _uiState.update { it.copy(errorMessage = "El video supera los 4 MB. Elige un video más corto.") }
                return@launch
            }
            val bytes = MediaUtils.readBytes(context, uri)
            _uiState.update {
                it.copy(
                    selectedMediaUri = uri,
                    selectedMediaBytes = bytes,
                    selectedMediaType = "video"
                )
            }
        }
    }

    fun removeSelectedMedia() {
        _uiState.update {
            it.copy(
                selectedMediaUri = null,
                selectedMediaBytes = null,
                selectedMediaType = "",
                selectedMediaDurationMs = 0L
            )
        }
    }

    // Grabación de notas de voz estilo WhatsApp
    fun startVoiceRecording(context: Context) {
        val manager = VoiceNoteManager(context)
        voiceNoteManager = manager
        if (manager.startRecording()) {
            _uiState.update { it.copy(isRecordingVoice = true, voiceRecordDurationMs = 0L) }
            viewModelScope.launch {
                while (_uiState.value.isRecordingVoice) {
                    manager.updateDuration()
                    val state = manager.recordState.value
                    _uiState.update {
                        it.copy(
                            voiceRecordDurationMs = state.durationMs,
                            voiceRecordAmplitude = state.amplitude
                        )
                    }
                    delay(100)
                }
            }
        } else {
            _uiState.update { it.copy(errorMessage = "No se pudo iniciar la grabación de audio.") }
        }
    }

    fun stopAndSendVoiceRecording() {
        val manager = voiceNoteManager ?: return
        val audioFile = manager.stopRecording()
        _uiState.update { it.copy(isRecordingVoice = false) }

        if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
            val dur = _uiState.value.voiceRecordDurationMs
            val bytes = audioFile.readBytes()
            sendVoiceNoteInternal(bytes, dur)
        }
    }

    fun cancelVoiceRecording() {
        voiceNoteManager?.cancelRecording()
        _uiState.update { it.copy(isRecordingVoice = false, voiceRecordDurationMs = 0L) }
    }

    private fun sendVoiceNoteInternal(audioBytes: ByteArray, durationMs: Long) {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return
        val replying = _uiState.value.replyingToMessage

        _uiState.update { it.copy(isSending = true) }
        viewModelScope.launch {
            try {
                repository.sendChatMessage(
                    chatId = active.id,
                    sender = user,
                    text = "",
                    mediaBytes = audioBytes,
                    filename = "voice_${System.currentTimeMillis()}.m4a",
                    mimeType = "audio/mp4",
                    mediaType = "audio",
                    mediaDurationMs = durationMs,
                    replyToMessageId = replying?.id,
                    replyToSenderName = replying?.senderDisplayName,
                    replyToText = replying?.text
                )
                val updatedDb = repository.getChatsDatabase(forceRemote = false)
                val updatedActive = if (active.isOfficialGroup) updatedDb.officialGroup else updatedDb.directChats.firstOrNull { it.id == active.id }
                _uiState.update {
                    it.copy(
                        activeChat = updatedActive ?: it.activeChat,
                        replyingToMessage = null,
                        isSending = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, errorMessage = "Error al enviar audio: ${e.message}") }
            }
        }
    }

    fun sendMessage() {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return
        val text = _uiState.value.messageInputText.trim()
        val mediaBytes = _uiState.value.selectedMediaBytes
        val mediaType = _uiState.value.selectedMediaType
        val replying = _uiState.value.replyingToMessage

        if (text.isBlank() && (mediaBytes == null || mediaBytes.isEmpty())) return

        _uiState.update { it.copy(isSending = true) }

        viewModelScope.launch {
            try {
                val mime = when (mediaType) {
                    "video" -> "video/mp4"
                    "audio" -> "audio/mp4"
                    else -> "image/jpeg"
                }
                val ext = when (mediaType) {
                    "video" -> "mp4"
                    "audio" -> "m4a"
                    else -> "jpg"
                }
                val filename = "upload_${System.currentTimeMillis()}.$ext"

                repository.sendChatMessage(
                    chatId = active.id,
                    sender = user,
                    text = text,
                    mediaBytes = mediaBytes,
                    filename = filename,
                    mimeType = mime,
                    mediaType = mediaType,
                    mediaDurationMs = _uiState.value.selectedMediaDurationMs,
                    replyToMessageId = replying?.id,
                    replyToSenderName = replying?.senderDisplayName,
                    replyToText = replying?.text
                )

                val updatedDb = repository.getChatsDatabase(forceRemote = false)
                val updatedActive = if (active.isOfficialGroup) updatedDb.officialGroup else updatedDb.directChats.firstOrNull { it.id == active.id }

                _uiState.update {
                    it.copy(
                        activeChat = updatedActive ?: it.activeChat,
                        messageInputText = "",
                        replyingToMessage = null,
                        selectedMediaUri = null,
                        selectedMediaBytes = null,
                        selectedMediaType = "",
                        isSending = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, errorMessage = "Error al enviar mensaje: ${e.message}")
                }
            }
        }
    }

    fun setMessageReaction(messageId: String, emoji: String) {
        val user = _uiState.value.currentUser ?: return
        val active = _uiState.value.activeChat ?: return

        viewModelScope.launch {
            try {
                repository.setChatMessageReaction(active.id, messageId, user.username, emoji)
                val updatedDb = repository.getChatsDatabase(forceRemote = false)
                val updatedActive = if (active.isOfficialGroup) updatedDb.officialGroup else updatedDb.directChats.firstOrNull { it.id == active.id }
                _uiState.update { it.copy(activeChat = updatedActive ?: it.activeChat) }
            } catch (_: Exception) {}
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
                val updatedDb = repository.getChatsDatabase(forceRemote = false)
                val updatedActive = if (active.isOfficialGroup) updatedDb.officialGroup else updatedDb.directChats.firstOrNull { it.id == active.id }
                _uiState.update {
                    it.copy(
                        activeChat = updatedActive ?: it.activeChat,
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
                val updatedDb = repository.getChatsDatabase(forceRemote = false)
                val updatedActive = if (active.isOfficialGroup) updatedDb.officialGroup else updatedDb.directChats.firstOrNull { it.id == active.id }
                _uiState.update { it.copy(activeChat = updatedActive ?: it.activeChat) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar mensaje: ${e.message}") }
            }
        }
    }

    fun startDirectChatWith(targetUsername: String, onChatReady: (String) -> Unit) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                val directChat = repository.getOrCreateDirectChat(user.username, targetUsername)
                _uiState.update { it.copy(activeChat = directChat, showNewChatDialog = false) }
                onChatReady(directChat.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al iniciar chat: ${e.message}") }
            }
        }
    }

    fun openNewChatDialog() {
        _uiState.update { it.copy(showNewChatDialog = true) }
    }

    fun closeNewChatDialog() {
        _uiState.update { it.copy(showNewChatDialog = false) }
    }

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
                    isAdmin = true
                )
                val updatedDb = repository.getChatsDatabase(forceRemote = false)
                _uiState.update {
                    it.copy(
                        officialGroup = updatedDb.officialGroup,
                        activeChat = if (it.activeChat?.isOfficialGroup == true) updatedDb.officialGroup else it.activeChat,
                        showManageGroupDialog = false,
                        isUpdatingGroup = false
                    )
                }
                resolveUsersAndAvatars()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isUpdatingGroup = false, errorMessage = "Error al actualizar grupo: ${e.message}")
                }
            }
        }
    }

    fun closeActiveChat() {
        currentChatIdToOpen = null
        _uiState.update {
            it.copy(
                activeChat = null,
                replyingToMessage = null,
                selectedMediaUri = null,
                selectedMediaBytes = null,
                selectedMediaType = "",
                editingMessageId = null,
                editingMessageText = ""
            )
        }
    }

    fun startDirectChat(targetUsername: String, onChatReady: (String) -> Unit) {
        startDirectChatWith(targetUsername, onChatReady)
    }

    fun reactToMessage(messageId: String, emoji: String) {
        setMessageReaction(messageId, emoji)
    }

    fun removeSelectedImage() {
        removeSelectedMedia()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
