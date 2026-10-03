package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.config.AppConfig
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

object MediaUtils {

    /**
     * Obtiene el tamaño en bytes de un archivo dado su Uri.
     */
    fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    -1L
                }
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Obtiene el nombre del archivo de un Uri.
     */
    fun getFileName(context: Context, uri: Uri): String {
        return try {
            var name = "documento_${System.currentTimeMillis()}"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex) ?: name
                }
            }
            name
        } catch (_: Exception) {
            "documento_${System.currentTimeMillis()}"
        }
    }

    /**
     * Lee los bytes de un Uri de forma segura verificando primero el tamaño
     * para evitar OutOfMemoryError en archivos grandes.
     */
    fun readBytes(
        context: Context,
        uri: Uri,
        maxAllowedBytes: Long = 15L * 1024 * 1024 // Límite estricto de 15 MB
    ): ByteArray? {
        val size = getFileSize(context, uri)
        if (size > maxAllowedBytes) {
            return null
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var bytesRead: Int
                var total = 0L
                while (input.read(chunk).also { bytesRead = it } != -1) {
                    total += bytesRead
                    if (total > maxAllowedBytes) return null
                    buffer.write(chunk, 0, bytesRead)
                }
                buffer.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Determina el tipo MIME de un Uri.
     */
    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "application/octet-stream"
    }

    /**
     * Comprime y redimensiona una imagen para que no exceda las dimensiones máximas
     * ni el límite de peso en bytes especificado.
     */
    fun compressImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = AppConfig.MAX_IMAGE_DIMENSION,
        maxBytes: Long = AppConfig.MAX_FILE_BYTES.toLong()
    ): ByteArray? {
        var inputStream: InputStream? = null
        try {
            // 1. Obtener dimensiones originales
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // 2. Calcular factor de submuestreo
            var inSampleSize = 1
            while ((origWidth / inSampleSize) > maxDimension || (origHeight / inSampleSize) > maxDimension) {
                inSampleSize *= 2
            }

            // 3. Decodificar imagen con tamaño reducido
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = context.contentResolver.openInputStream(uri)
            val sampledBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null
            inputStream?.close()

            // 4. Escalar exactamente al límite si aún lo supera
            val scale = maxDimension.toFloat() / maxOf(sampledBitmap.width, sampledBitmap.height).toFloat()
            val finalBitmap = if (scale < 1.0f) {
                val targetW = (sampledBitmap.width * scale).roundToInt()
                val targetH = (sampledBitmap.height * scale).roundToInt()
                val scaled = Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true)
                if (scaled != sampledBitmap) sampledBitmap.recycle()
                scaled
            } else {
                sampledBitmap
            }

            // 5. Compresión progresiva de calidad JPEG
            var quality = 85
            var resultBytes: ByteArray
            do {
                val outputStream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                resultBytes = outputStream.toByteArray()
                outputStream.close()
                quality -= 10
            } while (resultBytes.size > maxBytes && quality >= 30)

            finalBitmap.recycle()
            return resultBytes
        } catch (_: Exception) {
            return null
        } finally {
            inputStream?.close()
        }
    }

    /**
     * Formatea el tamaño de bytes a texto legible en MB o KB.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            val kb = bytes / 1024
            "$kb KB"
        }
    }

    /**
     * Formatea milisegundos a formato mm:ss para audios y videos.
     */
    fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
    }

    /**
     * Formatea un timestamp a tiempo relativo en español.
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        if (diff < 0) return "ahora mismo"

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30

        return when {
            seconds < 45 -> "ahora mismo"
            minutes < 60 -> "hace $minutes min"
            hours < 24 -> "hace $hours h"
            days == 1L -> "ayer"
            days < 7 -> "hace $days d"
            weeks < 4 -> "hace $weeks sem"
            months < 12 -> "hace $months m"
            else -> "hace más de 1 año"
        }
    }

    /**
     * Descarga un archivo directamente a la carpeta pública Downloads del dispositivo con notificación.
     */
    fun downloadFileToDownloads(context: Context, urlOrUri: String, filename: String) {
        if (urlOrUri.isBlank()) return
        val cleanName = if (filename.isNotBlank()) filename else "moodgram_${System.currentTimeMillis()}"
        try {
            if (urlOrUri.startsWith("http://") || urlOrUri.startsWith("https://")) {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val request = android.app.DownloadManager.Request(Uri.parse(urlOrUri)).apply {
                    setTitle(cleanName)
                    setDescription("Guardado en carpeta Descargas")
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, cleanName)
                    setAllowedOverMetered(true)
                    setAllowedOverRoaming(true)
                }
                dm.enqueue(request)
                android.widget.Toast.makeText(
                    context,
                    "Guardando en Descargas: $cleanName",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } else {
                openMediaExternally(context, urlOrUri)
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                context,
                "No se pudo descargar automáticamente: abriendo...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            openMediaExternally(context, urlOrUri)
        }
    }

    /**
     * Extrae un fotograma/miniatura de un video para mostrar en perfiles y listas.
     */
    fun getVideoThumbnail(videoUrl: String): Bitmap? {
        if (videoUrl.isBlank()) return null
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                retriever.setDataSource(videoUrl, HashMap<String, String>())
            } else {
                retriever.setDataSource(videoUrl)
            }
            val frame = retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()
            frame
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Intenta abrir o descargar/ver externamente un archivo o enlace
     */
    fun openMediaExternally(context: Context, urlOrUri: String) {
        if (urlOrUri.isBlank()) return
        try {
            val uri = Uri.parse(urlOrUri)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Abrir archivo").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            try {
                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(urlOrUri)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {}
        }
    }
}
