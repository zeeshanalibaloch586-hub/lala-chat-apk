package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.Report
import com.example.data.model.User

@Database(
    entities = [User::class, Chat::class, Message::class, Report::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LalaDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: LalaDatabase? = null

        fun getInstance(context: Context): LalaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LalaDatabase::class.java,
                    "lala_chat_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
