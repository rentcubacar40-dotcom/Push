package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Post
import com.example.data.model.UserSession
import com.example.data.repository.MoodgramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: Post? = null,
    val resolvedMediaUrl: String = "",
    val resolvedAvatarUrls: Map<String, String> = emptyMap(),
    val newCommentText: String = "",
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
        loadPost()
    }

    private fun observeSession() {
        viewModelScope.launch {
            repository.sessionManager.userSessionFlow.collectLatest { session ->
                _uiState.update { it.copy(currentUser = session) }
            }
        }
    }

    fun loadPost() {
        viewModelScope.launch {
            try {
                val db = repository.getPostsDatabase(forceRemote = false)
                val targetPost = db.posts.firstOrNull { it.id == postId }
                if (targetPost != null) {
                    val mediaUrl = repository.resolveMediaUrl(targetPost.fileRef.ifEmpty { targetPost.mediaUrl })
                    val usersDb = try { repository.getUsersDatabase(forceRemote = false) } catch (_: Exception) { null }
                    val userMap = usersDb?.users?.associateBy { it.username.lowercase() } ?: emptyMap()

                    val authorAvatarRef = if (targetPost.authorAvatarRef.isNotEmpty()) {
                        targetPost.authorAvatarRef
                    } else {
                        userMap[targetPost.authorUsername.lowercase()]?.avatarRef ?: ""
                    }

                    val avatarMap = mutableMapOf<String, String>()
                    if (authorAvatarRef.isNotEmpty()) {
                        val avatarUrl = repository.resolveMediaUrl(authorAvatarRef)
                        avatarMap[targetPost.authorAvatarRef] = avatarUrl
                        avatarMap[targetPost.authorUsername] = avatarUrl
                        avatarMap[authorAvatarRef] = avatarUrl
                    }

                    targetPost.comments.forEach { c ->
                        val cAvatarRef = if (c.authorAvatarRef.isNotEmpty()) {
                            c.authorAvatarRef
                        } else {
                            userMap[c.authorUsername.lowercase()]?.avatarRef ?: ""
                        }
                        if (cAvatarRef.isNotEmpty()) {
                            val cUrl = repository.resolveMediaUrl(cAvatarRef)
                            avatarMap[c.authorAvatarRef] = cUrl
                            avatarMap[c.authorUsername] = cUrl
                            avatarMap[cAvatarRef] = cUrl
                        }
                    }

                    _uiState.update {
                        it.copy(
                            post = targetPost,
                            resolvedMediaUrl = mediaUrl,
                            resolvedAvatarUrls = avatarMap,
                            isLoading = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Publicación no encontrada.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onCommentChange(text: String) {
        _uiState.update { it.copy(newCommentText = text) }
    }

    fun toggleLike() {
        val user = _uiState.value.currentUser ?: return
        val currentPost = _uiState.value.post ?: return

        val alreadyLiked = currentPost.likes.contains(user.username)
        val updatedLikes = if (alreadyLiked) currentPost.likes - user.username else currentPost.likes + user.username
        _uiState.update { it.copy(post = currentPost.copy(likes = updatedLikes)) }

        viewModelScope.launch {
            try {
                repository.toggleLike(postId, user.username)
            } catch (_: Exception) {}
        }
    }

    fun addComment() {
        val user = _uiState.value.currentUser ?: return
        val commentText = _uiState.value.newCommentText.trim()
        if (commentText.isBlank()) return

        _uiState.update { it.copy(isSubmittingComment = true) }
        viewModelScope.launch {
            try {
                val newComment = repository.addComment(postId, user, commentText)
                val currentPost = _uiState.value.post
                if (currentPost != null) {
                    val updatedComments = currentPost.comments + newComment
                    _uiState.update {
                        it.copy(
                            post = currentPost.copy(comments = updatedComments),
                            newCommentText = "",
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
