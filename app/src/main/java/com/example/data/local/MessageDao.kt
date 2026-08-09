package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isDeletedForUser = 0 ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Query("UPDATE messages SET isDeletedForUser = 1 WHERE messageId = :messageId")
    suspend fun markMessageDeletedForUser(messageId: String)

    @Query("UPDATE messages SET isDeletedForEveryone = 1, content = 'This message was deleted' WHERE messageId = :messageId")
    suspend fun markMessageDeletedForEveryone(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)

    @Query("SELECT * FROM messages WHERE status = 'FAILED'")
    suspend fun getFailedMessages(): List<Message>

    @Query("DELETE FROM messages")
    suspend fun clearMessages()
}
