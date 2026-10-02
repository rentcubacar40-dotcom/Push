package com.example.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.AppConfig
import com.example.data.repository.MoodgramRepository
import com.example.util.MediaUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoginTab: Boolean = true,
    val loginUsername: String = "",
    val loginPassword: String = "",
    val registerUsername: String = "",
    val registerDisplayName: String = "",
    val registerPassword: String = "",
    val registerConfirmPassword: String = "",
    val avatarUri: Uri? = null,
    val avatarBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setTab(isLogin: Boolean) {
        _uiState.update { it.copy(isLoginTab = isLogin, errorMessage = null) }
    }

    fun onLoginUsernameChange(value: String) {
        _uiState.update { it.copy(loginUsername = value, errorMessage = null) }
    }

    fun onLoginPasswordChange(value: String) {
        _uiState.update { it.copy(loginPassword = value, errorMessage = null) }
    }

    fun onRegisterUsernameChange(value: String) {
        _uiState.update { it.copy(registerUsername = value, errorMessage = null) }
    }

    fun onRegisterDisplayNameChange(value: String) {
        _uiState.update { it.copy(registerDisplayName = value, errorMessage = null) }
    }

    fun onRegisterPasswordChange(value: String) {
        _uiState.update { it.copy(registerPassword = value, errorMessage = null) }
    }

    fun onRegisterConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(registerConfirmPassword = value, errorMessage = null) }
    }

    fun onAvatarSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(avatarUri = null, avatarBytes = null) }
            return
        }

        viewModelScope.launch {
            val compressed = MediaUtils.compressImage(
                context = context,
                uri = uri,
                maxDimension = AppConfig.MAX_AVATAR_DIMENSION,
                maxBytes = AppConfig.MAX_AVATAR_BYTES.toLong()
            )
            _uiState.update {
                it.copy(avatarUri = uri, avatarBytes = compressed)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val username = state.loginUsername.trim()
        val password = state.loginPassword

        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Por favor ingresa usuario y contraseña.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.login(username, password)
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Error al iniciar sesión."
                    )
                }
            }
        }
    }

    fun register(onSuccess: () -> Unit) {
        val state = _uiState.value
        val username = state.registerUsername.trim()
        val displayName = state.registerDisplayName.trim()
        val password = state.registerPassword
        val confirmPassword = state.registerConfirmPassword

        if (username.isBlank() || displayName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Todos los campos son obligatorios.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(errorMessage = "La contraseña debe tener al menos 6 caracteres.") }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Las contraseñas no coinciden.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.register(
                    rawUsername = username,
                    displayName = displayName,
                    password = password,
                    avatarBytes = state.avatarBytes
                )
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Error al registrar usuario."
                    )
                }
            }
        }
    }
}
