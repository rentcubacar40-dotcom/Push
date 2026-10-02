package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.model.UserSession
import com.example.data.repository.MoodgramRepository
import com.example.util.MediaUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val userPosts: List<Post> = emptyList(),
    val totalLikes: Int = 0,
    val resolvedAvatarUrl: String = "",
    val resolvedThumbnails: Map<String, String> = emptyMap(),
    val currentUser: UserSession? = null,
    val isCurrentUser: Boolean = false,
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val isUpdating: Boolean = false,
    val editDisplayName: String = "",
    val editAvatarUri: Uri? = null,
    val editAvatarBytes: ByteArray? = null,
    val errorMessage: String? = null
)

class ProfileViewModel(
    private val repository: MoodgramRepository,
    private val targetUsername: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            repository.sessionManager.userSessionFlow.collectLatest { session ->
                _uiState.update { it.copy(currentUser = session) }
                refreshProfileData()
            }
        }

        viewModelScope.launch {
            repository.postsFlow.collectLatest {
                refreshProfileData()
            }
        }

        viewModelScope.launch {
            repository.usersFlow.collectLatest {
                refreshProfileData()
            }
        }
    }

    fun loadProfile() {
        refreshProfileData()
    }

    private fun refreshProfileData() {
        viewModelScope.launch {
            try {
                val session = repository.sessionManager.userSessionFlow.firstOrNull()
                val effectiveUsername = if (targetUsername == "profile_current" || targetUsername.isBlank()) {
                    session?.username ?: ""
                } else {
                    targetUsername
                }

                val usersDb = repository.usersFlow.value
                val targetUser = usersDb.users.firstOrNull {
                    it.username.equals(effectiveUsername, ignoreCase = true) ||
                    it.username.removePrefix("@").equals(effectiveUsername.removePrefix("@"), ignoreCase = true)
                }

                val postsDb = repository.postsFlow.value
                val userPosts = postsDb.posts.filter {
                    it.authorUsername.equals(effectiveUsername, ignoreCase = true) ||
                    it.authorUsername.removePrefix("@").equals(effectiveUsername.removePrefix("@"), ignoreCase = true)
                }

                val totalLikes = userPosts.sumOf { it.likes.size }

                val avatarUrl = if (!targetUser?.avatarRef.isNullOrEmpty()) {
                    repository.resolveMediaUrl(targetUser.avatarRef)
                } else ""

                val thumbs = mutableMapOf<String, String>()
                userPosts.forEach { post ->
                    val ref = post.fileRef.ifEmpty { post.mediaUrl }
                    if (ref.isNotEmpty()) {
                        thumbs[post.id] = repository.resolveMediaUrl(ref)
                    }
                }

                val isCurrent = session != null && session.username.equals(effectiveUsername, ignoreCase = true)

                _uiState.update {
                    it.copy(
                        user = targetUser,
                        userPosts = userPosts,
                        totalLikes = totalLikes,
                        resolvedAvatarUrl = avatarUrl,
                        resolvedThumbnails = thumbs,
                        currentUser = session,
                        isCurrentUser = isCurrent,
                        editDisplayName = targetUser?.displayName ?: it.editDisplayName,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun startEditing() {
        _uiState.update {
            it.copy(
                isEditing = true,
                editDisplayName = it.user?.displayName ?: "",
                editAvatarUri = null,
                editAvatarBytes = null
            )
        }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false) }
    }

    fun onEditDisplayNameChange(value: String) {
        _uiState.update { it.copy(editDisplayName = value) }
    }

    fun onNewAvatarSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(editAvatarUri = null, editAvatarBytes = null) }
            return
        }

        viewModelScope.launch {
            val bytes = MediaUtils.compressImage(
                context = context,
                uri = uri,
                maxDimension = AppConfig.MAX_AVATAR_DIMENSION,
                maxBytes = AppConfig.MAX_AVATAR_BYTES.toLong()
            )
            _uiState.update { it.copy(editAvatarUri = uri, editAvatarBytes = bytes) }
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        val user = state.user ?: return
        val newName = state.editDisplayName.trim()
        if (newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El nombre no puede estar vacío.") }
            return
        }

        _uiState.update { it.copy(isUpdating = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val updatedUser = repository.updateProfile(
                    username = user.username,
                    newDisplayName = newName,
                    newAvatarBytes = state.editAvatarBytes
                )

                val newAvatarUrl = if (updatedUser.avatarRef.isNotEmpty()) {
                    repository.resolveMediaUrl(updatedUser.avatarRef)
                } else state.resolvedAvatarUrl

                _uiState.update {
                    it.copy(
                        user = updatedUser,
                        resolvedAvatarUrl = newAvatarUrl,
                        isEditing = false,
                        isUpdating = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isUpdating = false, errorMessage = "Error al actualizar perfil: ${e.message}") }
            }
        }
    }

    fun openChatWithUser(onChatReady: (String) -> Unit) {
        val current = _uiState.value.currentUser ?: return
        val target = _uiState.value.user ?: return
        viewModelScope.launch {
            try {
                val directChat = repository.getOrCreateDirectChat(current.username, target.username)
                onChatReady(directChat.id)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al abrir chat: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
