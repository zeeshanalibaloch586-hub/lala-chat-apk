package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.data.local.LalaDatabase
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.util.NotificationHelper
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class ChatRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val db = LalaDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _typingStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap()) // chatId -> isOpponentTyping
    val typingStatusMap: StateFlow<Map<String, Boolean>> = _typingStatusMap.asStateFlow()

    private var messageListenerRegistration: ListenerRegistration? = null
    private var typingListenerRegistration: ListenerRegistration? = null
    private var userStatusListenerRegistration: ListenerRegistration? = null
    private var chatListenerRegistration: ListenerRegistration? = null

    fun getChatsFlow(): Flow<List<Chat>> = db.chatDao().getAllChatsFlow()

    fun getMessagesFlow(chatId: String): Flow<List<Message>> = db.messageDao().getMessagesForChatFlow(chatId)

    suspend fun getOrCreateChat(currentUserId: String, otherUser: User): Chat {
        val chatId = generateChatIdForPair(currentUserId, otherUser.userId)

        // Check if chat exists locally
        var chat = db.chatDao().getChatById(chatId)
        if (chat != null && !chat.isDeleted) return chat

        // Check if chat exists in Firestore
        try {
            val doc = firestore.collection("chats").document(chatId).get().await()
            if (doc.exists()) {
                val lastMsg = doc.getString("lastMessage") ?: "Start a conversation"
                val lastTs = doc.getLong("lastMessageTimestamp") ?: System.currentTimeMillis()
                val lastSender = doc.getString("lastMessageSenderId") ?: ""

                chat = Chat(
                    chatId = chatId,
                    participantIds = listOf(currentUserId, otherUser.userId),
                    otherUserId = otherUser.userId,
                    otherUserName = otherUser.displayName,
                    otherUserUsername = otherUser.username,
                    otherUserPhoto = otherUser.photoUrl,
                    otherUserChatId = otherUser.chatId,
                    lastMessage = lastMsg,
                    lastMessageTimestamp = lastTs,
                    lastMessageSenderId = lastSender,
                    unreadCount = 0,
                    isOtherUserOnline = otherUser.isOnline,
                    lastSeen = otherUser.lastSeen,
                    isDeleted = false
                )
                db.chatDao().insertChat(chat)
                return chat
            }
        } catch (_: Exception) {}

        // Create new conversation document in Firestore
        chat = Chat(
            chatId = chatId,
            participantIds = listOf(currentUserId, otherUser.userId),
            otherUserId = otherUser.userId,
            otherUserName = otherUser.displayName,
            otherUserUsername = otherUser.username,
            otherUserPhoto = otherUser.photoUrl,
            otherUserChatId = otherUser.chatId,
            lastMessage = "Start a conversation",
            lastMessageTimestamp = System.currentTimeMillis(),
            lastMessageSenderId = "",
            unreadCount = 0,
            isOtherUserOnline = otherUser.isOnline,
            lastSeen = otherUser.lastSeen,
            isDeleted = false
        )

        db.chatDao().insertChat(chat)
        try {
            val firestoreData = mapOf(
                "chatId" to chatId,
                "participantIds" to listOf(currentUserId, otherUser.userId),
                "lastMessage" to "Start a conversation",
                "lastMessageTimestamp" to chat.lastMessageTimestamp,
                "lastMessageSenderId" to ""
            )
            firestore.collection("chats").document(chatId).set(firestoreData, SetOptions.merge()).await()
        } catch (_: Exception) {}

        return chat
    }

    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        mediaName: String? = null,
        voiceDurationMs: Long? = null,
        replyToMessageId: String? = null,
        replyToContent: String? = null,
        replyToSenderName: String? = null,
        isForwarded: Boolean = false
    ): Result<Message> {
        val msgId = "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6)
        val message = Message(
            messageId = msgId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            timestamp = System.currentTimeMillis(),
            type = type,
            status = MessageStatus.SENT, // ✓
            mediaUrl = mediaUrl,
            mediaName = mediaName,
            voiceDurationMs = voiceDurationMs,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSenderName = replyToSenderName,
            isForwarded = isForwarded
        )

        // Save locally in Room immediately
        db.messageDao().insertMessage(message)

        // Update chat summary
        val previewText = when (type) {
            MessageType.TEXT -> content
            MessageType.IMAGE -> "📷 Photo"
            MessageType.FILE -> "📄 ${mediaName ?: "File"}"
            MessageType.VOICE -> "🎤 Voice message"
        }

        val existingChat = db.chatDao().getChatById(chatId)
        if (existingChat != null) {
            val updatedChat = existingChat.copy(
                lastMessage = previewText,
                lastMessageTimestamp = System.currentTimeMillis(),
                lastMessageSenderId = senderId,
                isDeleted = false
            )
            db.chatDao().insertChat(updatedChat)
        }

        // Push to Firestore asynchronously
        scope.launch {
            try {
                firestore.collection("chats").document(chatId)
                    .collection("messages").document(msgId).set(message).await()

                firestore.collection("chats").document(chatId).update(
                    mapOf(
                        "lastMessage" to previewText,
                        "lastMessageTimestamp" to message.timestamp,
                        "lastMessageSenderId" to senderId
                    )
                ).await()
            } catch (e: Exception) {
                // If network fails, mark as failed for retry
                val failedMsg = message.copy(status = MessageStatus.FAILED)
                db.messageDao().insertMessage(failedMsg)
            }
        }

        return Result.success(message)
    }

    suspend fun sendMediaMessage(
        chatId: String,
        senderId: String,
        receiverId: String,
        type: MessageType,
        mediaUri: Uri,
        mediaName: String? = null,
        voiceDurationMs: Long? = null,
        caption: String = "",
        replyToMessageId: String? = null,
        replyToContent: String? = null,
        replyToSenderName: String? = null
    ): Result<Message> {
        val msgId = "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().take(6)
        val initialContent = if (caption.isNotBlank()) caption else when (type) {
            MessageType.IMAGE -> "Photo"
            MessageType.FILE -> mediaName ?: "File Attachment"
            MessageType.VOICE -> "Voice message"
            else -> ""
        }

        val message = Message(
            messageId = msgId,
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            content = initialContent,
            timestamp = System.currentTimeMillis(),
            type = type,
            status = MessageStatus.SENDING,
            mediaUrl = mediaUri.toString(),
            mediaName = mediaName ?: if (type == MessageType.VOICE) "voice_note.mp3" else "file",
            voiceDurationMs = voiceDurationMs,
            replyToMessageId = replyToMessageId,
            replyToContent = replyToContent,
            replyToSenderName = replyToSenderName
        )

        // Save locally in Room immediately with SENDING status
        db.messageDao().insertMessage(message)

        val previewText = when (type) {
            MessageType.TEXT -> initialContent
            MessageType.IMAGE -> "📷 Photo"
            MessageType.FILE -> "📄 ${mediaName ?: "File"}"
            MessageType.VOICE -> "🎤 Voice message"
        }

        val existingChat = db.chatDao().getChatById(chatId)
        if (existingChat != null) {
            val updatedChat = existingChat.copy(
                lastMessage = previewText,
                lastMessageTimestamp = System.currentTimeMillis(),
                lastMessageSenderId = senderId,
                isDeleted = false
            )
            db.chatDao().insertChat(updatedChat)
        }

        // Upload to Firebase Storage in background
        scope.launch {
            try {
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("chats/$chatId/${type.name.lowercase()}/${msgId}_${mediaName ?: "media"}")

                val downloadUrl: String = if (type == MessageType.IMAGE) {
                    val bytes = try {
                        val inputStream = context.contentResolver.openInputStream(mediaUri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        val baos = ByteArrayOutputStream()
                        bitmap?.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        baos.toByteArray()
                    } catch (_: Exception) {
                        null
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        storageRef.putBytes(bytes).await()
                    } else {
                        storageRef.putFile(mediaUri).await()
                    }
                    storageRef.downloadUrl.await().toString()
                } else {
                    storageRef.putFile(mediaUri).await()
                    storageRef.downloadUrl.await().toString()
                }

                val sentMessage = message.copy(
                    mediaUrl = downloadUrl,
                    status = MessageStatus.SENT
                )

                db.messageDao().insertMessage(sentMessage)

                firestore.collection("chats").document(chatId)
                    .collection("messages").document(msgId).set(sentMessage).await()

                firestore.collection("chats").document(chatId).update(
                    mapOf(
                        "lastMessage" to previewText,
                        "lastMessageTimestamp" to message.timestamp,
                        "lastMessageSenderId" to senderId
                    )
                ).await()
            } catch (e: Exception) {
                val failedMsg = message.copy(status = MessageStatus.FAILED)
                db.messageDao().insertMessage(failedMsg)
            }
        }

        return Result.success(message)
    }

    suspend fun retryMediaMessage(message: Message) {
        if (message.status != MessageStatus.FAILED) return

        val sendingMsg = message.copy(status = MessageStatus.SENDING)
        db.messageDao().insertMessage(sendingMsg)

        val previewText = when (message.type) {
            MessageType.TEXT -> message.content
            MessageType.IMAGE -> "📷 Photo"
            MessageType.FILE -> "📄 ${message.mediaName ?: "File"}"
            MessageType.VOICE -> "🎤 Voice message"
        }

        scope.launch {
            try {
                var finalMediaUrl = message.mediaUrl
                if (finalMediaUrl.isNullOrEmpty() || finalMediaUrl.startsWith("content://") || finalMediaUrl.startsWith("file://")) {
                    val uri = Uri.parse(message.mediaUrl ?: "")
                    val storageRef = FirebaseStorage.getInstance().reference
                        .child("chats/${message.chatId}/${message.type.name.lowercase()}/${message.messageId}_${message.mediaName ?: "media"}")
                    storageRef.putFile(uri).await()
                    finalMediaUrl = storageRef.downloadUrl.await().toString()
                }

                val sentMsg = sendingMsg.copy(mediaUrl = finalMediaUrl, status = MessageStatus.SENT)
                db.messageDao().insertMessage(sentMsg)

                firestore.collection("chats").document(message.chatId)
                    .collection("messages").document(message.messageId).set(sentMsg).await()

                firestore.collection("chats").document(message.chatId).update(
                    mapOf(
                        "lastMessage" to previewText,
                        "lastMessageTimestamp" to sentMsg.timestamp,
                        "lastMessageSenderId" to sentMsg.senderId
                    )
                ).await()
            } catch (_: Exception) {
                val failedMsg = message.copy(status = MessageStatus.FAILED)
                db.messageDao().insertMessage(failedMsg)
            }
        }
    }

    fun startListeningToMessages(chatId: String, currentUserId: String) {
        messageListenerRegistration?.remove()
        typingListenerRegistration?.remove()
        userStatusListenerRegistration?.remove()

        scope.launch {
            val localChat = db.chatDao().getChatById(chatId)
            val otherUid = localChat?.otherUserId
            if (!otherUid.isNullOrEmpty()) {
                userStatusListenerRegistration = firestore.collection("users").document(otherUid)
                    .addSnapshotListener { userDoc, _ ->
                        if (userDoc != null && userDoc.exists()) {
                            val isOnline = userDoc.getBoolean("isOnline") ?: false
                            val lastSeen = userDoc.getLong("lastSeen") ?: System.currentTimeMillis()
                            scope.launch {
                                val currentChat = db.chatDao().getChatById(chatId)
                                if (currentChat != null) {
                                    val updatedChat = currentChat.copy(
                                        isOtherUserOnline = isOnline,
                                        lastSeen = lastSeen
                                    )
                                    db.chatDao().insertChat(updatedChat)
                                }
                            }
                        }
                    }
            }
        }

        messageListenerRegistration = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val remoteMessages = snapshot.toObjects(Message::class.java)
                scope.launch {
                    db.messageDao().insertMessages(remoteMessages)

                    // Auto mark received messages as SEEN if receiver is active in this chat
                    remoteMessages.filter { it.receiverId == currentUserId && it.status != MessageStatus.SEEN }
                        .forEach { unread ->
                            markMessageSeen(chatId, unread.messageId)
                        }

                    // Check newly added incoming messages for notifications
                    for (dc in snapshot.documentChanges) {
                        if (dc.type == DocumentChange.Type.ADDED) {
                            val msg = dc.document.toObject(Message::class.java)
                            if (msg.receiverId == currentUserId && msg.senderId != currentUserId) {
                                val otherName = db.chatDao().getChatById(chatId)?.otherUserName ?: "Lala User"
                                val preview = when (msg.type) {
                                    MessageType.TEXT -> msg.content
                                    MessageType.IMAGE -> "📷 Photo"
                                    MessageType.FILE -> "📄 ${msg.mediaName}"
                                    MessageType.VOICE -> "🎤 Voice message"
                                }
                                NotificationHelper.showMessageNotification(
                                    context = context,
                                    senderName = otherName,
                                    messageText = preview,
                                    chatId = chatId,
                                    senderId = msg.senderId,
                                    messageId = msg.messageId
                                )
                            }
                        }
                    }
                }
            }

        // Listen to typing status real-time updates
        typingListenerRegistration = firestore.collection("chats").document(chatId)
            .collection("typing").document("status")
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val map = doc.data ?: emptyMap()
                    val isOpponentTyping = map.entries.any { it.key != currentUserId && it.value == true }
                    val current = _typingStatusMap.value.toMutableMap()
                    current[chatId] = isOpponentTyping
                    _typingStatusMap.value = current
                } else {
                    val current = _typingStatusMap.value.toMutableMap()
                    current[chatId] = false
                    _typingStatusMap.value = current
                }
            }
    }

    fun startListeningToUserChats(currentUserId: String) {
        chatListenerRegistration?.remove()
        chatListenerRegistration = firestore.collection("chats")
            .whereArrayContains("participantIds", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                scope.launch {
                    for (doc in snapshot.documents) {
                        @Suppress("UNCHECKED_CAST")
                        val participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                        val otherUserId = participantIds.firstOrNull { it != currentUserId } ?: continue

                        var otherUser = db.userDao().getUserById(otherUserId)
                        try {
                            val userDoc = firestore.collection("users").document(otherUserId).get().await()
                            if (userDoc.exists()) {
                                val fetchedUser = userDoc.toObject(User::class.java)
                                if (fetchedUser != null) {
                                    otherUser = fetchedUser
                                    db.userDao().insertUser(fetchedUser)
                                }
                            }
                        } catch (_: Exception) {}

                        val chatId = doc.id
                        val existingLocalChat = db.chatDao().getChatById(chatId)

                        val lastMsg = doc.getString("lastMessage") ?: "Start a conversation"
                        val lastTs = doc.getLong("lastMessageTimestamp") ?: System.currentTimeMillis()
                        val lastSender = doc.getString("lastMessageSenderId") ?: ""

                        val chat = Chat(
                            chatId = chatId,
                            participantIds = participantIds,
                            otherUserId = otherUserId,
                            otherUserName = otherUser?.displayName ?: "Lala User",
                            otherUserUsername = otherUser?.username ?: "",
                            otherUserPhoto = otherUser?.photoUrl ?: "",
                            otherUserChatId = otherUser?.chatId ?: "",
                            lastMessage = lastMsg,
                            lastMessageTimestamp = lastTs,
                            lastMessageSenderId = lastSender,
                            unreadCount = existingLocalChat?.unreadCount ?: 0,
                            isOtherUserOnline = otherUser?.isOnline ?: false,
                            lastSeen = otherUser?.lastSeen ?: System.currentTimeMillis(),
                            isDeleted = false
                        )
                        db.chatDao().insertChat(chat)

                        // Check for SENT messages sent to currentUserId to mark as DELIVERED
                        if (lastSender.isNotEmpty() && lastSender != currentUserId) {
                            try {
                                val unreadSent = firestore.collection("chats").document(chatId)
                                    .collection("messages")
                                    .whereEqualTo("receiverId", currentUserId)
                                    .whereEqualTo("status", MessageStatus.SENT.name)
                                    .get().await()

                                for (mDoc in unreadSent.documents) {
                                    markMessageDelivered(chatId, mDoc.id)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
    }

    fun stopListeningToUserChats() {
        chatListenerRegistration?.remove()
        chatListenerRegistration = null
    }

    fun stopListeningToMessages() {
        messageListenerRegistration?.remove()
        messageListenerRegistration = null
        typingListenerRegistration?.remove()
        typingListenerRegistration = null
        userStatusListenerRegistration?.remove()
        userStatusListenerRegistration = null
    }

    fun setTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        scope.launch {
            try {
                firestore.collection("chats").document(chatId)
                    .collection("typing").document("status")
                    .set(mapOf(userId to isTyping), SetOptions.merge())
            } catch (_: Exception) {}
        }
    }

    private suspend fun markMessageDelivered(chatId: String, messageId: String) {
        val msg = db.messageDao().getMessageById(messageId)
        if (msg != null && msg.status == MessageStatus.SENT) {
            val delMsg = msg.copy(status = MessageStatus.DELIVERED)
            db.messageDao().insertMessage(delMsg)
        }

        try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("status", MessageStatus.DELIVERED.name)
        } catch (_: Exception) {}
    }

    private suspend fun markMessageSeen(chatId: String, messageId: String) {
        val msg = db.messageDao().getMessageById(messageId) ?: return
        val seenMsg = msg.copy(status = MessageStatus.SEEN)
        db.messageDao().insertMessage(seenMsg)

        try {
            firestore.collection("chats").document(chatId)
                .collection("messages").document(messageId)
                .update("status", MessageStatus.SEEN.name)
        } catch (_: Exception) {}
    }

    suspend fun toggleReaction(chatId: String, messageId: String, userId: String, emoji: String) {
        val msg = db.messageDao().getMessageById(messageId) ?: return
        val reactions = msg.reactions.toMutableMap()
        if (reactions[userId] == emoji) {
            reactions.remove(userId)
        } else {
            reactions[userId] = emoji
        }

        val updated = msg.copy(reactions = reactions)
        db.messageDao().insertMessage(updated)

        scope.launch {
            try {
                firestore.collection("chats").document(chatId)
                    .collection("messages").document(messageId)
                    .update("reactions", reactions)
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String, deleteForEveryone: Boolean) {
        if (deleteForEveryone) {
            db.messageDao().markMessageDeletedForEveryone(messageId)
            scope.launch {
                try {
                    firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId)
                        .update(
                            mapOf(
                                "isDeletedForEveryone" to true,
                                "content" to "This message was deleted"
                            )
                        )
                } catch (_: Exception) {}
            }
        } else {
            db.messageDao().markMessageDeletedForUser(messageId)
        }
    }

    suspend fun deleteChatConversation(chatId: String) {
        db.chatDao().softDeleteChat(chatId)
        db.messageDao().deleteMessagesForChat(chatId)
    }

    suspend fun retryFailedMessages() {
        val failed = db.messageDao().getFailedMessages()
        for (msg in failed) {
            scope.launch {
                try {
                    val retried = msg.copy(status = MessageStatus.SENT)
                    firestore.collection("chats").document(msg.chatId)
                        .collection("messages").document(msg.messageId).set(retried).await()
                    db.messageDao().insertMessage(retried)
                } catch (_: Exception) {}
            }
        }
    }

    private fun generateChatIdForPair(uid1: String, uid2: String): String {
        val sorted = listOf(uid1, uid2).sorted()
        return "chat_${sorted[0]}_${sorted[1]}"
    }
}
