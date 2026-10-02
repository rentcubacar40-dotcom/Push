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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatePostUiState(
    val selectedUri: Uri? = null,
    val isVideo: Boolean = false,
    val isTextOnlyMode: Boolean = false,
    val selectedGradient: String = "teal",
    val fileSize: Long = 0L,
    val fileSizeFormatted: String = "0 MB",
    val isOverLimit: Boolean = false,
    val overLimitMessage: String? = null,
    val captionText: String = "",
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class CreatePostViewModel(
    private val repository: MoodgramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    fun onCaptionChange(text: String) {
        _uiState.update { it.copy(captionText = text) }
    }

    fun setTextOnlyMode(isTextOnly: Boolean) {
        _uiState.update { it.copy(isTextOnlyMode = isTextOnly) }
    }

    fun selectGradient(gradient: String) {
        _uiState.update { it.copy(selectedGradient = gradient) }
    }

    fun onMediaSelected(context: Context, uri: Uri?) {
        if (uri == null) {
            _uiState.update {
                it.copy(
                    selectedUri = null,
                    isVideo = false,
                    fileSize = 0L,
                    fileSizeFormatted = "0 MB",
                    isOverLimit = false,
                    overLimitMessage = null
                )
            }
            return
        }

        val mimeType = MediaUtils.getMimeType(context, uri)
        val isVideo = mimeType.startsWith("video/")
        val sizeBytes = MediaUtils.getFileSize(context, uri)
        val formatted = MediaUtils.formatFileSize(sizeBytes)
        val isOver = sizeBytes > AppConfig.MAX_FILE_BYTES

        val overLimitMsg = if (isOver && isVideo) {
            "El video supera el límite de 4 MB (pesa $formatted). Selecciona un video más corto o comprimido."
        } else if (isOver && !isVideo) {
            "La imagen pesa $formatted. Se comprimirá automáticamente a menos de 4 MB para subirla."
        } else {
            null
        }

        _uiState.update {
            it.copy(
                selectedUri = uri,
                isVideo = isVideo,
                isTextOnlyMode = false,
                fileSize = sizeBytes,
                fileSizeFormatted = formatted,
                isOverLimit = isOver && isVideo, // Solo bloquea si es video mayor a 4MB
                overLimitMessage = overLimitMsg,
                errorMessage = null
            )
        }
    }

    fun publish(context: Context, onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.isTextOnlyMode) {
            if (state.captionText.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Escribe el texto de tu publicación.") }
                return
            }

            _uiState.update {
                it.copy(isUploading = true, uploadProgress = 0.5f, errorMessage = null)
            }

            viewModelScope.launch {
                try {
                    val session = repository.sessionManager.userSessionFlow.firstOrNull()
                        ?: throw IllegalStateException("Sesión no iniciada.")

                    repository.createPost(
                        author = session,
                        text = state.captionText,
                        mediaBytes = null,
                        filename = "",
                        mimeType = "",
                        isVideo = false,
                        backgroundColor = state.selectedGradient,
                        onProgress = { progress ->
                            _uiState.update { it.copy(uploadProgress = progress) }
                        }
                    )

                    _uiState.update {
                        it.copy(isUploading = false, uploadProgress = 1f, isSuccess = true)
                    }
                    onSuccess()
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 0f,
                            errorMessage = e.message ?: "Error al publicar."
                        )
                    }
                }
            }
            return
        }

        val uri = state.selectedUri ?: run {
            _uiState.update { it.copy(errorMessage = "Selecciona una imagen, video o activa el modo Solo Texto.") }
            return
        }

        if (state.isOverLimit && state.isVideo) {
            _uiState.update {
                it.copy(errorMessage = "No se puede subir un video mayor a 4 MB. Elige uno más corto.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isUploading = true,
                uploadProgress = 0.05f,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val session = repository.sessionManager.userSessionFlow.firstOrNull()
                    ?: throw IllegalStateException("Sesión no iniciada.")

                val mimeType = MediaUtils.getMimeType(context, uri)
                val isVideo = state.isVideo

                // Preparar bytes: comprimir imagen si es necesario
                val mediaBytes = if (!isVideo) {
                    MediaUtils.compressImage(
                        context = context,
                        uri = uri,
                        maxDimension = AppConfig.MAX_IMAGE_DIMENSION,
                        maxBytes = AppConfig.MAX_FILE_BYTES.toLong()
                    ) ?: throw IllegalStateException("No se pudo procesar la imagen.")
                } else {
                    MediaUtils.readBytes(context, uri)
                        ?: throw IllegalStateException("No se pudo leer el archivo de video.")
                }

                if (mediaBytes.size > AppConfig.MAX_FILE_BYTES) {
                    throw IllegalStateException("El archivo final supera los 4 MB (${MediaUtils.formatFileSize(mediaBytes.size.toLong())}).")
                }

                val ext = if (isVideo) "mp4" else "jpg"
                val originalName = "upload_$ext"

                repository.createPost(
                    author = session,
                    text = state.captionText,
                    mediaBytes = mediaBytes,
                    filename = originalName,
                    mimeType = if (isVideo) "video/mp4" else "image/jpeg",
                    isVideo = isVideo,
                    onProgress = { progress ->
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                )

                _uiState.update {
                    it.copy(isUploading = false, uploadProgress = 1f, isSuccess = true)
                }
                onSuccess()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        errorMessage = e.message ?: "Error al publicar en la nube."
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
