package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.repository.MoodgramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val users: List<User> = emptyList(),
    val posts: List<Post> = emptyList(),
    val totalStorageBytes: Long = 0L,
    val totalComments: Int = 0,
    val activeUsersCount: Int = 0,
    val bannedUsersCount: Int = 0,
    val searchQuery: String = "",
    val selectedTab: Int = 0, // 0: Usuarios, 1: Publicaciones, 2: Sistema
    val isLoading: Boolean = true,
    val isActionInProgress: Boolean = false,
    val serverStatus: String = "Conectado al servidor en la nube",
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AdminViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    init {
        loadAdminData()
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun loadAdminData() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val usersDb = repository.getUsersDatabase(forceRemote = true)
                val postsDb = repository.getPostsDatabase(forceRemote = true)

                val storage = postsDb.posts.sumOf { it.fileSize }
                val comments = postsDb.posts.sumOf { it.comments.size }
                val active = usersDb.users.count { !it.isBanned }
                val banned = usersDb.users.count { it.isBanned }

                _uiState.update {
                    it.copy(
                        users = usersDb.users,
                        posts = postsDb.posts,
                        totalStorageBytes = storage,
                        totalComments = comments,
                        activeUsersCount = active,
                        bannedUsersCount = banned,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleBanUser(targetUser: User) {
        _uiState.update { it.copy(isActionInProgress = true) }
        viewModelScope.launch {
            try {
                val newBannedState = !targetUser.isBanned
                repository.setUserBannedStatus(targetUser.username, newBannedState)

                val updatedUsers = _uiState.value.users.map {
                    if (it.username.equals(targetUser.username, ignoreCase = true)) {
                        it.copy(isBanned = newBannedState)
                    } else it
                }

                _uiState.update {
                    it.copy(
                        users = updatedUsers,
                        isActionInProgress = false,
                        successMessage = if (newBannedState) "Usuario suspendido." else "Usuario reactivado."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isActionInProgress = false, errorMessage = e.message) }
            }
        }
    }

    fun deleteUser(targetUser: User) {
        _uiState.update { it.copy(isActionInProgress = true) }
        viewModelScope.launch {
            try {
                repository.deleteUser(targetUser.username)
                val updatedUsers = _uiState.value.users.filterNot {
                    it.username.equals(targetUser.username, ignoreCase = true)
                }
                _uiState.update {
                    it.copy(
                        users = updatedUsers,
                        isActionInProgress = false,
                        successMessage = "Usuario ${targetUser.username} eliminado permanentemente."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isActionInProgress = false, errorMessage = e.message) }
            }
        }
    }

    fun deletePost(postId: String) {
        _uiState.update { it.copy(isActionInProgress = true) }
        viewModelScope.launch {
            try {
                repository.deletePost(postId, "@Eliel_21", isAdmin = true)
                val updatedPosts = _uiState.value.posts.filterNot { it.id == postId }
                _uiState.update {
                    it.copy(
                        posts = updatedPosts,
                        isActionInProgress = false,
                        successMessage = "Publicación eliminada por el administrador."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isActionInProgress = false, errorMessage = e.message) }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
