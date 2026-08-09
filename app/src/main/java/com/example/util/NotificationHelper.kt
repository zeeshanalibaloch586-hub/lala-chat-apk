package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.repository.UserRepository

object NotificationHelper {
    private const val CHANNEL_ID = "lala_chat_messages_v2"
    private const val CHANNEL_NAME = "Lala Chat Messages"

    // Tracks currently active conversation to prevent duplicate notification when user is already in the chat
    @Volatile
    var activeChatId: String? = null

    // Set of recently notified message IDs to prevent duplicate alerts
    private val recentlyNotifiedMsgs = mutableSetOf<String>()

    fun showMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        chatId: String,
        senderId: String = "",
        messageId: String = ""
    ) {
        try {
            val prefs = context.getSharedPreferences("lala_settings", Context.MODE_PRIVATE)

            // 1. Master toggle check
            val notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
            if (!notificationsEnabled) return

            // 2. Message notifications toggle check
            val messageNotificationsEnabled = prefs.getBoolean("message_notifications_enabled", true)
            if (!messageNotificationsEnabled) return

            // 3. Active Chat check (suppress notification if user is currently viewing this conversation)
            if (activeChatId != null && activeChatId == chatId) {
                return
            }

            // 4. Blocked User check
            if (senderId.isNotBlank()) {
                val userRepository = UserRepository(context)
                if (userRepository.isUserBlocked(senderId)) {
                    return
                }
            }

            // 5. Duplicate check by message ID
            if (messageId.isNotBlank()) {
                synchronized(recentlyNotifiedMsgs) {
                    if (recentlyNotifiedMsgs.contains(messageId)) return
                    recentlyNotifiedMsgs.add(messageId)
                    if (recentlyNotifiedMsgs.size > 100) {
                        recentlyNotifiedMsgs.clear()
                    }
                }
            }

            val soundEnabled = prefs.getBoolean("notification_sound_enabled", true)
            val vibrationEnabled = prefs.getBoolean("vibration_enabled", true)

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new messages in Lala Chat"
                    enableVibration(vibrationEnabled)
                    if (!vibrationEnabled) {
                        vibrationPattern = longArrayOf(0L)
                    }
                    if (!soundEnabled) {
                        setSound(null, null)
                    }
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("target_chat_id", chatId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                chatId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(senderName)
                .setContentText(messageText)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            if (!soundEnabled) {
                builder.setSound(null)
            }
            if (!vibrationEnabled) {
                builder.setVibrate(longArrayOf(0L))
            }

            notificationManager.notify(chatId.hashCode(), builder.build())
        } catch (_: Exception) {
            // Safe fallback: avoid crashes on notification failure
        }
    }
}
