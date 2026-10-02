package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.UserSession
import com.example.data.repository.MoodgramRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val currentUser: UserSession? = null,
    val themeMode: String = "SYSTEM",
    val cacheCleared: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessionManager.userSessionFlow.collectLatest { session ->
                _uiState.update { it.copy(currentUser = session) }
            }
        }
        viewModelScope.launch {
            repository.sessionManager.themeModeFlow.collectLatest { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.sessionManager.setThemeMode(mode)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.localCache.clear()
            _uiState.update { it.copy(message = "Caché local borrada con éxito.") }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            repository.sessionManager.clearSession()
            onLoggedOut()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
