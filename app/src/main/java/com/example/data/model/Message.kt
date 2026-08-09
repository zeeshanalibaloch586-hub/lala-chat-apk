package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    IMAGE,
    FILE,
    VOICE
}

enum class MessageStatus {
    SENDING,
    SENT,       // ✓
    DELIVERED,  // ✓✓
    SEEN,       // ✓✓ (emerald)
    FAILED      // ! (retry)
}

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val status: MessageStatus = MessageStatus.SENT,
    val mediaUrl: String? = null,
    val mediaName: String? = null,
    val voiceDurationMs: Long? = null,
    val replyToMessageId: String? = null,
    val replyToContent: String? = null,
    val replyToSenderName: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val isDeletedForEveryone: Boolean = false,
    val isDeletedForUser: Boolean = false,
    val isForwarded: Boolean = false
)
