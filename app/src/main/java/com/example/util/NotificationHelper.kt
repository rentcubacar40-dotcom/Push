package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_ID = "moodgram_messages_v2"
    private const val CHANNEL_NAME = "Mensajes y Notificaciones"
    private const val CHANNEL_DESC = "Notificaciones de nuevos mensajes, menciones y actividad en Moodgram"

    /**
     * ID del chat que el usuario tiene abierto actualmente en primer plano.
     * Evita emitir sonido/notificación cuando el usuario ya está leyendo la conversación.
     */
    @Volatile
    var currentActiveChatId: String? = null

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showChatNotification(
        context: Context,
        senderDisplayName: String,
        messageText: String,
        chatId: String,
        notificationId: Int = (chatId.hashCode() and 0x7FFFFFFF)
    ) {
        // No notificar si el chat ya está en pantalla activa
        if (currentActiveChatId == chatId || com.example.data.repository.MoodgramRepository.currentActiveChatId == chatId) {
            return
        }

        initNotificationChannel(context)

        if (!hasNotificationPermission(context)) {
            return
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("extra_chat_id", chatId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(senderDisplayName)
                .setContentText(messageText.ifBlank { "Nuevo archivo adjunto" })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setGroup("moodgram_group_$chatId")

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (_: Exception) {}
    }
}
