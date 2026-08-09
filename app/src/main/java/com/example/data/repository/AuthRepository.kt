package com.example.data.repository

import android.content.Context
import com.example.data.local.LalaDatabase
import com.example.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.random.Random

class AuthRepository(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val db = LalaDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _authState = MutableStateFlow<Boolean>(false)
    val authState: StateFlow<Boolean> = _authState.asStateFlow()

    init {
        // Persistent session via Firebase Auth state listener
        auth.addAuthStateListener { firebaseAuth ->
            val fbUser = firebaseAuth.currentUser
            if (fbUser != null) {
                loadUserProfile(fbUser.uid, fbUser.email, fbUser.displayName, fbUser.photoUrl?.toString())
            } else {
                _currentUser.value = null
                _authState.value = false
            }
        }
    }

    private fun loadUserProfile(
        userId: String,
        fallbackEmail: String? = null,
        fallbackName: String? = null,
        fallbackPhoto: String? = null
    ) {
        scope.launch {
            try {
                var user: User? = null
                try {
                    val snapshot = firestore.collection("users").document(userId).get().await()
                    if (snapshot.exists()) {
                        user = snapshot.toObject(User::class.java)
                    }
                } catch (_: Exception) {}

                if (user == null) {
                    user = db.userDao().getUserById(userId)
                }

                if (user == null && !fallbackEmail.isNullOrBlank()) {
                    val username = fallbackEmail.substringBefore("@").lowercase()
                    val chatId = generateUniqueChatId()
                    user = User(
                        userId = userId,
                        displayName = fallbackName?.ifBlank { "Lala User" } ?: "Lala User",
                        username = username,
                        chatId = chatId,
                        email = fallbackEmail,
                        photoUrl = fallbackPhoto?.ifBlank { "https://picsum.photos/seed/$username/200" } ?: "https://picsum.photos/seed/$username/200",
                        bio = "Hey there! I am using Lala Chat.",
                        createdAt = System.currentTimeMillis(),
                        isOnline = true,
                        lastSeen = System.currentTimeMillis()
                    )
                    try {
                        firestore.collection("users").document(userId).set(user).await()
                    } catch (_: Exception) {}
                    db.userDao().insertUser(user)
                }

                if (user != null) {
                    db.userDao().insertUser(user)
                    _currentUser.value = user
                    _authState.value = true
                    updatePresence(userId, true)
                    refreshFcmToken(userId)
                } else {
                    _authState.value = false
                }
            } catch (_: Exception) {
                _authState.value = false
            }
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        displayName: String,
        username: String
    ): Result<User> {
        return try {
            val cleanUsername = username.trim().removePrefix("@").lowercase()

            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user ?: throw Exception("Firebase Auth creation returned null user.")
            val uid = firebaseUser.uid

            val chatId = generateUniqueChatId()
            val newUser = User(
                userId = uid,
                displayName = displayName.ifBlank { "Lala User" },
                username = cleanUsername,
                chatId = chatId,
                email = email,
                photoUrl = "https://picsum.photos/seed/$cleanUsername/200",
                bio = "Hey there! I am using Lala Chat.",
                createdAt = System.currentTimeMillis(),
                isOnline = true,
                lastSeen = System.currentTimeMillis()
            )

            try {
                firestore.collection("users").document(uid).set(newUser).await()
            } catch (_: Exception) {}
            db.userDao().insertUser(newUser)

            _currentUser.value = newUser
            _authState.value = true

            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithEmail(email: String, pass: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, pass).await()
            val firebaseUser = authResult.user ?: throw Exception("Firebase Auth login returned null user.")
            val uid = firebaseUser.uid

            var user: User? = null
            try {
                val snapshot = firestore.collection("users").document(uid).get().await()
                if (snapshot.exists()) {
                    user = snapshot.toObject(User::class.java)
                }
            } catch (_: Exception) {}

            if (user == null) {
                user = db.userDao().getUserById(uid)
            }

            if (user == null) {
                val username = email.substringBefore("@").lowercase()
                val chatId = generateUniqueChatId()
                user = User(
                    userId = uid,
                    displayName = firebaseUser.displayName?.ifBlank { "Lala User" } ?: "Lala User",
                    username = username,
                    chatId = chatId,
                    email = email,
                    photoUrl = firebaseUser.photoUrl?.toString() ?: "https://picsum.photos/seed/$username/200",
                    bio = "Hey there! I am using Lala Chat.",
                    createdAt = System.currentTimeMillis(),
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                try {
                    firestore.collection("users").document(uid).set(user).await()
                } catch (_: Exception) {}
                db.userDao().insertUser(user)
            } else {
                db.userDao().insertUser(user)
            }

            _currentUser.value = user
            _authState.value = true
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUserIdToken(forceRefresh: Boolean = false): Result<String> {
        val firebaseUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("User is not signed in to Firebase Auth."))
        return try {
            val tokenResult = firebaseUser.getIdToken(forceRefresh).await()
            val token = tokenResult.token
            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("Firebase Auth returned an empty idToken."))
            } else {
                Result.success(token)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithGoogleToken(idToken: String): Result<User> {
        if (idToken.isBlank()) {
            return Result.failure(IllegalArgumentException("Google Sign-In error: idToken cannot be empty."))
        }
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Google Sign-In failed.")
            val uid = firebaseUser.uid
            val email = firebaseUser.email ?: ""
            val name = firebaseUser.displayName ?: "Google User"
            val photo = firebaseUser.photoUrl?.toString() ?: ""

            var user: User? = null
            try {
                val snapshot = firestore.collection("users").document(uid).get().await()
                if (snapshot.exists()) {
                    user = snapshot.toObject(User::class.java)
                }
            } catch (_: Exception) {}

            if (user == null) {
                val username = if (email.contains("@")) email.substringBefore("@").lowercase() else "user_${uid.take(6)}"
                val chatId = generateUniqueChatId()
                user = User(
                    userId = uid,
                    displayName = name,
                    username = username,
                    chatId = chatId,
                    email = email,
                    photoUrl = photo.ifBlank { "https://picsum.photos/seed/$username/200" },
                    bio = "Hey there! I am using Lala Chat.",
                    createdAt = System.currentTimeMillis(),
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                try {
                    firestore.collection("users").document(uid).set(user).await()
                } catch (_: Exception) {}
                db.userDao().insertUser(user)
            } else {
                db.userDao().insertUser(user)
            }

            _currentUser.value = user
            _authState.value = true
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        username: String,
        photoUrl: String,
        bio: String
    ): Result<User> {
        val curr = _currentUser.value ?: return Result.failure(Exception("Not logged in"))
        return try {
            val cleanUsername = username.trim().removePrefix("@").lowercase()

            val updatedUser = curr.copy(
                displayName = displayName.ifBlank { curr.displayName },
                username = cleanUsername,
                photoUrl = photoUrl.ifBlank { curr.photoUrl },
                bio = bio
            )

            db.userDao().insertUser(updatedUser)
            try {
                firestore.collection("users").document(curr.userId).set(updatedUser).await()
            } catch (_: Exception) {}

            _currentUser.value = updatedUser
            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        val curr = _currentUser.value
        if (curr != null) {
            scope.launch { updatePresence(curr.userId, false) }
        }
        try { auth.signOut() } catch (_: Exception) {}

        _currentUser.value = null
        _authState.value = false
    }

    fun updatePresence(userId: String, isOnline: Boolean) {
        scope.launch {
            try {
                val user = db.userDao().getUserById(userId)
                if (user != null) {
                    val updated = user.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
                    db.userDao().insertUser(updated)
                    firestore.collection("users").document(userId).update(
                        mapOf("isOnline" to isOnline, "lastSeen" to System.currentTimeMillis())
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshFcmToken(userId: String) {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (!token.isNullOrBlank()) {
                        updateFcmToken(userId, token)
                    }
                }
        } catch (_: Exception) {}
    }

    fun updateFcmToken(userId: String, token: String) {
        scope.launch {
            try {
                val user = db.userDao().getUserById(userId)
                if (user != null) {
                    val updated = user.copy(fcmToken = token)
                    db.userDao().insertUser(updated)
                }
                firestore.collection("users").document(userId).update("fcmToken", token).await()
            } catch (_: Exception) {}
        }
    }

    private fun generateUniqueChatId(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val code = (1..4).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "LALA-$code"
    }
}

