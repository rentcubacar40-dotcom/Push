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
     * Lee los bytes completos de un Uri.
     */
    fun readBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
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
        return if (mb >= 0.1) {
            "%.1f MB".format(mb)
        } else {
            val kb = bytes / 1024
            "$kb KB"
        }
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
}
