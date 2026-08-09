package com.example.service

import com.example.data.repository.AuthRepository
import com.example.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId != null) {
                val authRepository = AuthRepository(applicationContext)
                authRepository.updateFcmToken(currentUserId, token)
            }
        } catch (_: Exception) {}
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        try {
            val data = remoteMessage.data
            val senderName = data["senderName"] ?: remoteMessage.notification?.title ?: "Lala Chat"
            val messageText = data["message"] ?: remoteMessage.notification?.body ?: "New message"
            val chatId = data["chatId"] ?: ""
            val senderId = data["senderId"] ?: ""
            val messageId = data["messageId"] ?: ""

            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return

            // If message is sent by current user, ignore
            if (senderId.isNotBlank() && senderId == currentUserId) return

            NotificationHelper.showMessageNotification(
                context = applicationContext,
                senderName = senderName,
                messageText = messageText,
                chatId = chatId,
                senderId = senderId,
                messageId = messageId
            )
        } catch (_: Exception) {}
    }
}
