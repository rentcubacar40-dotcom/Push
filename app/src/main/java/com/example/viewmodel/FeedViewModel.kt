package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Post
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

data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val resolvedMediaUrls: Map<String, String> = emptyMap(),
    val resolvedAvatarUrls: Map<String, String> = emptyMap(),
    val userOnlineStatus: Map<String, Boolean> = emptyMap(),
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
                delay(4000)
                try {
                    val session = _uiState.value.currentUser
                    if (session != null) {
                        repository.updateHeartbeat(session.username)
                    }
                    val remoteDb = repository.getPostsDatabase(forceRemote = false)
                    val currentCount = _uiState.value.posts.size
                    if (remoteDb.posts.size != currentCount || remoteDb.posts != _uiState.value.posts) {
                        _uiState.update { it.copy(posts = remoteDb.posts) }
                        resolveUrlsForPosts(remoteDb.posts)
                    }
                } catch (_: Exception) {}
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

                // 2. Si es remoto o inicial, actualizar en segundo plano
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
                        errorMessage = if (!hasCache) "No se pudo sincronizar el feed con el servidor." else null
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
        val currentMedia = mutableMapOf<String, String>()
        val currentAvatars = mutableMapOf<String, String>()
        val onlineStatus = mutableMapOf<String, Boolean>()

        val usersDb = try {
            repository.getUsersDatabase(forceRemote = false)
        } catch (_: Exception) {
            null
        }
        val userMap = usersDb?.users?.associateBy { it.username.lowercase() } ?: emptyMap()

        posts.forEach { post ->
            val ref = post.fileRef.ifEmpty { post.mediaUrl }
            if (ref.isNotEmpty()) {
                val url = repository.resolveMediaUrl(ref)
                currentMedia[post.id] = url
            }

            val userInDb = userMap[post.authorUsername.lowercase()]
            val avatarRef = userInDb?.avatarRef?.ifEmpty { post.authorAvatarRef } ?: post.authorAvatarRef

            if (avatarRef.isNotEmpty()) {
                val url = repository.resolveMediaUrl(avatarRef)
                currentAvatars[avatarRef] = url
                currentAvatars[post.authorUsername] = url
            }

            onlineStatus[post.authorUsername] = userInDb?.isOnline ?: false
        }

        _uiState.update {
            it.copy(
                resolvedMediaUrls = currentMedia,
                resolvedAvatarUrls = currentAvatars,
                userOnlineStatus = onlineStatus
            )
        }
    }

    fun setReaction(post: Post, emoji: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
                // Optimistic UI update
                val current = post.reactions[user.username]
                val updatedReactions = post.reactions.toMutableMap()
                if (current == emoji) {
                    updatedReactions.remove(user.username)
                } else {
                    updatedReactions[user.username] = emoji
                }
                val updatedPosts = _uiState.value.posts.map {
                    if (it.id == post.id) it.copy(reactions = updatedReactions, likes = updatedReactions.keys.toList()) else it
                }
                _uiState.update { it.copy(posts = updatedPosts) }

                repository.setPostReaction(post.id, user.username, emoji)
            } catch (_: Exception) {}
        }
    }

    fun toggleLike(post: Post) {
        setReaction(post, "❤️")
    }

    fun deletePost(postId: String) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            try {
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
