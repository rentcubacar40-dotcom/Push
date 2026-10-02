package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class VoiceRecordState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val outputFile: File? = null,
    val amplitude: Int = 0
)

class VoiceNoteManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTime: Long = 0L

    private val _recordState = MutableStateFlow(VoiceRecordState())
    val recordState: StateFlow<VoiceRecordState> = _recordState.asStateFlow()

    fun startRecording(): Boolean {
        return try {
            stopRecordingInternal()

            val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.m4a")
            currentFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTime = System.currentTimeMillis()
            _recordState.value = VoiceRecordState(
                isRecording = true,
                durationMs = 0L,
                outputFile = file
            )
            true
        } catch (e: Exception) {
            Log.e("VoiceNoteManager", "Error al iniciar grabación", e)
            _recordState.value = VoiceRecordState(isRecording = false)
            false
        }
    }

    fun updateDuration() {
        if (_recordState.value.isRecording && startTime > 0) {
            val dur = System.currentTimeMillis() - startTime
            val amp = try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (_: Exception) {
                0
            }
            _recordState.value = _recordState.value.copy(
                durationMs = dur,
                amplitude = amp
            )
        }
    }

    fun stopRecording(): File? {
        val file = currentFile
        stopRecordingInternal()
        return if (file != null && file.exists() && file.length() > 0) {
            file
        } else {
            null
        }
    }

    fun cancelRecording() {
        val file = currentFile
        stopRecordingInternal()
        try {
            file?.delete()
        } catch (_: Exception) {}
    }

    private fun stopRecordingInternal() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Log.w("VoiceNoteManager", "Aviso al detener grabador", e)
        } finally {
            mediaRecorder = null
            _recordState.value = VoiceRecordState(isRecording = false)
        }
    }

    companion object {
        fun getAudioDurationMs(context: Context, uri: Uri): Long {
            return try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                retriever.release()
                durationStr?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
