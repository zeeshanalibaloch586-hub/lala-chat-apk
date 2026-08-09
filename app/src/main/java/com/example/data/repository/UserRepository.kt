package com.example.data.repository

import android.content.Context
import com.example.data.local.LalaDatabase
import com.example.data.model.Report
import com.example.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UserRepository(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val db = LalaDatabase.getInstance(context)

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds = _blockedUserIds

    init {
        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        val prefs = context.getSharedPreferences("lala_privacy_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("blocked_users", emptySet()) ?: emptySet()
        _blockedUserIds.value = set
    }

    suspend fun searchUser(query: String, currentUserId: String = ""): List<User> {
        val clean = query.trim().removePrefix("@")
        if (clean.isBlank()) return emptyList()

        val resultMap = mutableMapOf<String, User>()

        try {
            // Search Firestore by Chat ID (e.g. LALA-88B2)
            val byChatId = firestore.collection("users")
                .whereEqualTo("chatId", clean.uppercase())
                .get().await()

            for (doc in byChatId.documents) {
                val u = doc.toObject(User::class.java)
                if (u != null && u.userId != currentUserId) {
                    resultMap[u.userId] = u
                }
            }

            // Search Firestore by Username
            val byUsername = firestore.collection("users")
                .whereEqualTo("username", clean.lowercase())
                .get().await()

            for (doc in byUsername.documents) {
                val u = doc.toObject(User::class.java)
                if (u != null && u.userId != currentUserId) {
                    resultMap[u.userId] = u
                }
            }

            // Fallback: search local room DB
            val localResults = db.userDao().searchUsers(clean.lowercase())
            for (u in localResults) {
                if (u.userId != currentUserId && !resultMap.containsKey(u.userId)) {
                    resultMap[u.userId] = u
                }
            }

            val list = resultMap.values.toList()
            if (list.isNotEmpty()) {
                db.userDao().insertUsers(list)
            }
            return list
        } catch (e: Exception) {
            return db.userDao().searchUsers(clean.lowercase()).filter { it.userId != currentUserId }
        }
    }

    suspend fun getUserProfile(userId: String): User? {
        val local = db.userDao().getUserById(userId)
        if (local != null) return local

        return try {
            val doc = firestore.collection("users").document(userId).get().await()
            val user = doc.toObject(User::class.java)
            if (user != null) {
                db.userDao().insertUser(user)
            }
            user
        } catch (e: Exception) {
            null
        }
    }

    fun getUserFlow(userId: String): Flow<User?> = db.userDao().getUserByIdFlow(userId)

    fun blockUser(userId: String) {
        val current = _blockedUserIds.value.toMutableSet()
        current.add(userId)
        _blockedUserIds.value = current

        context.getSharedPreferences("lala_privacy_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("blocked_users", current).apply()
    }

    fun unblockUser(userId: String) {
        val current = _blockedUserIds.value.toMutableSet()
        current.remove(userId)
        _blockedUserIds.value = current

        context.getSharedPreferences("lala_privacy_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("blocked_users", current).apply()
    }

    fun isUserBlocked(userId: String): Boolean = _blockedUserIds.value.contains(userId)

    suspend fun reportUser(reporterId: String, reportedUserId: String, reason: String, details: String): Boolean {
        return try {
            val report = Report(
                reportId = "rep_" + UUID.randomUUID().toString().take(8),
                reporterId = reporterId,
                reportedUserId = reportedUserId,
                reason = reason,
                details = details,
                timestamp = System.currentTimeMillis()
            )
            firestore.collection("reports").document(report.reportId).set(report).await()
            true
        } catch (e: Exception) {
            true
        }
    }
}
