package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val chatId: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val bio: String = "Hey there! I am using Lala Chat.",
    val createdAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val fcmToken: String = ""
)
