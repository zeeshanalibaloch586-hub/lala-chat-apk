package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("lala_settings", Context.MODE_PRIVATE)

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("is_dark_theme", true)) // default AMOLED dark
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isAppLockEnabled = MutableStateFlow(prefs.getBoolean("app_lock_enabled", false))
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    private val _isAppLocked = MutableStateFlow(_isAppLockEnabled.value)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean("notifications_enabled", true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _messageNotificationsEnabled = MutableStateFlow(prefs.getBoolean("message_notifications_enabled", true))
    val messageNotificationsEnabled: StateFlow<Boolean> = _messageNotificationsEnabled.asStateFlow()

    private val _notificationSoundEnabled = MutableStateFlow(prefs.getBoolean("notification_sound_enabled", true))
    val notificationSoundEnabled: StateFlow<Boolean> = _notificationSoundEnabled.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    private val _readReceiptsEnabled = MutableStateFlow(prefs.getBoolean("read_receipts", true))
    val readReceiptsEnabled: StateFlow<Boolean> = _readReceiptsEnabled.asStateFlow()

    private val _lastSeenVisibility = MutableStateFlow(prefs.getString("last_seen_visibility", "Everyone") ?: "Everyone")
    val lastSeenVisibility: StateFlow<String> = _lastSeenVisibility.asStateFlow()

    fun toggleDarkTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
        prefs.edit().putBoolean("is_dark_theme", isDark).apply()
    }

    fun setAppLockEnabled(enabled: Boolean, pin: String = "1234") {
        _isAppLockEnabled.value = enabled
        _isAppLocked.value = enabled
        prefs.edit()
            .putBoolean("app_lock_enabled", enabled)
            .putString("app_lock_pin", pin)
            .apply()
    }

    fun verifyPin(enteredPin: String): Boolean {
        val savedPin = prefs.getString("app_lock_pin", "1234")
        if (enteredPin == savedPin) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun toggleNotifications(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun toggleMessageNotifications(enabled: Boolean) {
        _messageNotificationsEnabled.value = enabled
        prefs.edit().putBoolean("message_notifications_enabled", enabled).apply()
    }

    fun toggleNotificationSound(enabled: Boolean) {
        _notificationSoundEnabled.value = enabled
        prefs.edit().putBoolean("notification_sound_enabled", enabled).apply()
    }

    fun toggleVibration(enabled: Boolean) {
        _vibrationEnabled.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun toggleReadReceipts(enabled: Boolean) {
        _readReceiptsEnabled.value = enabled
        prefs.edit().putBoolean("read_receipts", enabled).apply()
    }

    fun setLastSeenVisibility(visibility: String) {
        _lastSeenVisibility.value = visibility
        prefs.edit().putString("last_seen_visibility", visibility).apply()
    }
}
