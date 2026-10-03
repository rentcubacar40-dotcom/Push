package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Comment
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.model.UserSession
import com.example.data.repository.MoodgramRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: Post? = null,
    val resolvedMediaUrl: String = "",
    val resolvedAvatarUrls: Map<String, String> = emptyMap(),
    val userOnlineStatus: Map<String, Boolean> = emptyMap(),
    val allUsers: List<User> = emptyList(),
    val newCommentText: String = "",
    val replyingToComment: Comment? = null,
    val showReactionsDetail: Boolean = false,
    val editingCommentId: String? = null,
    val editingCommentText: String = "",
    val isSubmittingComment: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val currentUser: UserSession? = null,
    val isPostDeleted: Boolean = false
)

class PostDetailViewModel(
    private val repository: MoodgramRepository,
    private val postId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState(isLoading = true))
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        observePostsFlow()
        loadPost()
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

    private fun observePostsFlow() {
        viewModelScope.launch {
            repository.postsFlow.collectLatest { db ->
                val targetPost = db.posts.firstOrNull { it.id == postId }
                if (targetPost != null && targetPost != _uiState.value.post) {
                    _uiState.update { it.copy(post = targetPost, isLoading = false) }
                    resolveAvatarsAndMedia(targetPost)
                }
            }
        }
    }

    private fun startRealtimePolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                try {
                    repository.syncPosts(forceRemote = true)
                } catch (_: Exception) {}
            }
        }
    }

    fun loadPost() {
        viewModelScope.launch {
            try {
                val cachedPost = repository.postsFlow.value.posts.firstOrNull { it.id == postId }
                if (cachedPost != null) {
                    _uiState.update { it.copy(post = cachedPost, isLoading = false) }
                    resolveAvatarsAndMedia(cachedPost)
                }

                repository.syncPosts(forceRemote = true)
                val targetPost = repository.postsFlow.value.posts.firstOrNull { it.id == postId }
                if (targetPost != null) {
                    _uiState.update { it.copy(post = targetPost, isLoading = false) }
                    resolveAvatarsAndMedia(targetPost)
                } else if (cachedPost == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Publicación no encontrada.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private suspend fun resolveAvatarsAndMedia(targetPost: Post) {
        val mediaUrl = repository.resolveMediaUrl(targetPost.fileRef.ifEmpty { targetPost.mediaUrl })
        val usersDb = try { repository.getUsersDatabase(forceRemote = false) } catch (_: Exception) { null }
        val userMap = usersDb?.users?.associateBy { it.username.lowercase() } ?: emptyMap()

        val avatarMap = mutableMapOf<String, String>()
        val onlineMap = mutableMapOf<String, Boolean>()

        val authorUser = userMap[targetPost.authorUsername.lowercase()]
        val authorAvatarRef = authorUser?.avatarRef?.ifEmpty { targetPost.authorAvatarRef } ?: targetPost.authorAvatarRef
        if (authorAvatarRef.isNotEmpty()) {
            val aUrl = repository.resolveMediaUrl(authorAvatarRef)
            avatarMap[targetPost.authorUsername] = aUrl
            avatarMap[authorAvatarRef] = aUrl
        }
        onlineMap[targetPost.authorUsername] = authorUser?.isOnline ?: false

        targetPost.comments.forEach { c ->
            val cUser = userMap[c.authorUsername.lowercase()]
            val cAvatarRef = cUser?.avatarRef?.ifEmpty { c.authorAvatarRef } ?: c.authorAvatarRef
            if (cAvatarRef.isNotEmpty()) {
                val cUrl = repository.resolveMediaUrl(cAvatarRef)
                avatarMap[c.authorUsername] = cUrl
                avatarMap[cAvatarRef] = cUrl
            }
            onlineMap[c.authorUsername] = cUser?.isOnline ?: false
        }

        _uiState.update {
            it.copy(
                resolvedMediaUrl = mediaUrl,
                resolvedAvatarUrls = avatarMap,
                userOnlineStatus = onlineMap,
                allUsers = usersDb?.users ?: emptyList()
            )
        }
    }

    fun onCommentChange(text: String) {
        _uiState.update { it.copy(newCommentText = text) }
    }

    fun startEditingComment(commentId: String, currentText: String) {
        _uiState.update { it.copy(editingCommentId = commentId, editingCommentText = currentText) }
    }

    fun onEditingCommentTextChange(text: String) {
        _uiState.update { it.copy(editingCommentText = text) }
    }

    fun cancelEditingComment() {
        _uiState.update { it.copy(editingCommentId = null, editingCommentText = "") }
    }

    fun saveEditedComment() {
        val user = _uiState.value.currentUser ?: return
        val commentId = _uiState.value.editingCommentId ?: return
        val newText = _uiState.value.editingCommentText.trim()
        if (newText.isBlank()) return

        viewModelScope.launch {
            try {
                repository.editComment(postId, commentId, newText, user.username, user.isAdmin)
                val currentPost = _uiState.value.post
                if (currentPost != null) {
                    val updatedComments = currentPost.comments.map {
                        if (it.id == commentId) it.copy(text = newText, isEdited = true, editedAt = System.currentTimeMillis()) else it
                    }
                    _uiState.update {
                        it.copy(
                            post = currentPost.copy(comments = updatedComments),
                            editingCommentId = null,
                            editingCommentText = ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al editar comentario: ${e.message}") }
            }
        }
    }

    fun setReaction(emoji: String) {
        val user = _uiState.value.currentUser ?: return
        val currentPost = _uiState.value.post ?: return

        val currentReaction = currentPost.reactions[user.username]
        val updatedReactions = currentPost.reactions.toMutableMap()
        if (currentReaction == emoji) {
            updatedReactions.remove(user.username)
        } else {
            updatedReactions[user.username] = emoji
        }
        val updatedLikes = updatedReactions.keys.toList()
        _uiState.update { it.copy(post = currentPost.copy(reactions = updatedReactions, likes = updatedLikes)) }

        viewModelScope.launch {
            try {
                repository.setPostReaction(postId, user.username, emoji)
            } catch (_: Exception) {}
        }
    }

    fun toggleLike() {
        setReaction("❤️")
    }

    fun setReplyingToComment(comment: Comment?) {
        _uiState.update { it.copy(replyingToComment = comment) }
    }

    fun setShowReactionsDetail(show: Boolean) {
        _uiState.update { it.copy(showReactionsDetail = show) }
    }

    fun addComment() {
        val user = _uiState.value.currentUser ?: return
        val commentText = _uiState.value.newCommentText.trim()
        val replyingTo = _uiState.value.replyingToComment
        if (commentText.isBlank()) return

        _uiState.update { it.copy(isSubmittingComment = true) }
        viewModelScope.launch {
            try {
                val newComment = repository.addComment(
                    postId = postId,
                    user = user,
                    commentText = commentText,
                    replyToCommentId = replyingTo?.id,
                    replyToUsername = replyingTo?.authorUsername,
                    replyToDisplayName = replyingTo?.authorDisplayName
                )
                val currentPost = _uiState.value.post
                if (currentPost != null) {
                    val updatedComments = currentPost.comments + newComment
                    _uiState.update {
                        it.copy(
                            post = currentPost.copy(comments = updatedComments),
                            newCommentText = "",
                            replyingToComment = null,
                            isSubmittingComment = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSubmittingComment = false, errorMessage = "Error al comentar: ${e.message}")
                }
            }
        }
    }

    fun deleteComment(commentId: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                val currentPost = _uiState.value.post ?: return@launch
                val updatedComments = currentPost.comments.filterNot { it.id == commentId }
                _uiState.update { it.copy(post = currentPost.copy(comments = updatedComments)) }
                repository.deleteComment(postId, commentId, user.username, user.isAdmin)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar comentario: ${e.message}") }
            }
        }
    }

    fun deletePost(onDeleted: () -> Unit) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                repository.deletePost(postId, user.username, user.isAdmin)
                _uiState.update { it.copy(isPostDeleted = true) }
                onDeleted()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar publicación: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
