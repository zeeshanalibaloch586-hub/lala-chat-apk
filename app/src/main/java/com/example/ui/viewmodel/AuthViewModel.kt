package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val authRepo = AuthRepository(application)

    val currentUser: StateFlow<User?> = authRepo.currentUser
    val authState: StateFlow<Boolean> = authRepo.authState

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signUp(email: String, pass: String, displayName: String, username: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val res = authRepo.signUpWithEmail(email, pass, displayName, username)
            res.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { err -> _uiState.value = AuthUiState.Error(err.message ?: "Sign up failed") }
            )
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val res = authRepo.loginWithEmail(email, pass)
            res.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { err -> _uiState.value = AuthUiState.Error(err.message ?: "Login failed") }
            )
        }
    }

    fun googleLoginWithToken(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val res = authRepo.loginWithGoogleToken(idToken)
            res.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { err -> _uiState.value = AuthUiState.Error(err.message ?: "Google login failed") }
            )
        }
    }




    fun setOnlineStatus(isOnline: Boolean) {
        val uid = currentUser.value?.userId ?: return
        authRepo.updatePresence(uid, isOnline)
    }

    fun updateProfile(displayName: String, username: String, photoUrl: String, bio: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val res = authRepo.updateProfile(displayName, username, photoUrl, bio)
            res.fold(
                onSuccess = { user -> _uiState.value = AuthUiState.Success(user) },
                onFailure = { err -> _uiState.value = AuthUiState.Error(err.message ?: "Update failed") }
            )
        }
    }

    fun resetPassword(email: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            authRepo.resetPassword(email)
            onDone("Password reset email sent if account exists.")
        }
    }

    fun logout() {
        authRepo.logout()
        _uiState.value = AuthUiState.Idle
    }

    fun resetError() {
        _uiState.value = AuthUiState.Idle
    }
}
