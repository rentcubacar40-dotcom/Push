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

data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val resolvedMediaUrls: Map<String, String> = emptyMap(),
    val resolvedAvatarUrls: Map<String, String> = emptyMap(),
    val currentUser: UserSession? = null
)

class FeedViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        loadFeed(forceRemote = false)
    }

    private fun observeSession() {
        viewModelScope.launch {
            repository.sessionManager.userSessionFlow.collectLatest { session ->
                _uiState.update { it.copy(currentUser = session) }
            }
        }
    }

    fun loadFeed(forceRemote: Boolean = false) {
        val hasCache = _uiState.value.posts.isNotEmpty()
        if (!hasCache) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        }

        viewModelScope.launch {
            try {
                // 1. Cargar local primero (instantáneo)
                val localDb = repository.getPostsDatabase(forceRemote = false)
                _uiState.update { it.copy(posts = localDb.posts, isLoading = false) }

                resolveUrlsForPosts(localDb.posts)

                // 2. Si es remoto o inicial, actualizar en segundo plano con Moodle
                if (forceRemote || !hasCache) {
                    val remoteDb = repository.getPostsDatabase(forceRemote = true)
                    _uiState.update {
                        it.copy(
                            posts = remoteDb.posts,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    resolveUrlsForPosts(remoteDb.posts)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (!hasCache) "No se pudo sincronizar el feed con Moodle." else null
                    )
                }
            }
        }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        loadFeed(forceRemote = true)
    }

    private suspend fun resolveUrlsForPosts(posts: List<Post>) {
        val currentMedia = _uiState.value.resolvedMediaUrls.toMutableMap()
        val currentAvatars = _uiState.value.resolvedAvatarUrls.toMutableMap()

        posts.forEach { post ->
            if (!currentMedia.containsKey(post.id) && (post.fileRef.isNotEmpty() || post.mediaUrl.isNotEmpty())) {
                val ref = post.fileRef.ifEmpty { post.mediaUrl }
                val url = repository.resolveMediaUrl(ref)
                currentMedia[post.id] = url
            }
            if (post.authorAvatarRef.isNotEmpty() && !currentAvatars.containsKey(post.authorAvatarRef)) {
                val url = repository.resolveMediaUrl(post.authorAvatarRef)
                currentAvatars[post.authorAvatarRef] = url
            }
        }

        _uiState.update {
            it.copy(
                resolvedMediaUrls = currentMedia,
                resolvedAvatarUrls = currentAvatars
            )
        }
    }

    fun toggleLike(post: Post) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                // Optimistic UI update
                val isLiked = post.likes.contains(user.username)
                val updatedLikes = if (isLiked) post.likes - user.username else post.likes + user.username
                val updatedPosts = _uiState.value.posts.map {
                    if (it.id == post.id) it.copy(likes = updatedLikes) else it
                }
                _uiState.update { it.copy(posts = updatedPosts) }

                // Sync with Moodle
                repository.toggleLike(post.id, user.username)
            } catch (_: Exception) {}
        }
    }

    fun deletePost(postId: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                // Optimistic removal
                _uiState.update { state ->
                    state.copy(posts = state.posts.filterNot { it.id == postId })
                }
                repository.deletePost(postId, user.username, user.isAdmin)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error al eliminar: ${e.message}") }
                loadFeed(forceRemote = false)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
