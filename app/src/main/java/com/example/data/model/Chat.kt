package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey val chatId: String = "",
    val participantIds: List<String> = emptyList(),
    val otherUserId: String = "",
    val otherUserName: String = "",
    val otherUserUsername: String = "",
    val otherUserPhoto: String = "",
    val otherUserChatId: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val lastMessageSenderId: String = "",
    val unreadCount: Int = 0,
    val isOtherUserOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val isBlocked: Boolean = false,
    val isDeleted: Boolean = false
)
