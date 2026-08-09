package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToBlockedUsers: () -> Unit,
    onBack: () -> Unit
) {
    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()
    val isAppLockEnabled by settingsViewModel.isAppLockEnabled.collectAsState()
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState()
    val messageNotificationsEnabled by settingsViewModel.messageNotificationsEnabled.collectAsState()
    val notificationSoundEnabled by settingsViewModel.notificationSoundEnabled.collectAsState()
    val vibrationEnabled by settingsViewModel.vibrationEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "APPEARANCE & THEME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )

            SettingsSwitchRow(
                icon = Icons.Default.DarkMode,
                title = "AMOLED Dark Theme",
                subtitle = "Pure black background with emerald green accent",
                checked = isDarkTheme,
                onCheckedChange = { settingsViewModel.toggleDarkTheme(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "PRIVACY & SECURITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )

            SettingsSwitchRow(
                icon = Icons.Default.Lock,
                title = "App Lock",
                subtitle = "Require PIN code to open Lala Chat",
                checked = isAppLockEnabled,
                onCheckedChange = { settingsViewModel.setAppLockEnabled(it) }
            )

            SettingsClickableRow(
                icon = Icons.Default.Security,
                title = "Privacy Settings",
                subtitle = "Last seen, read receipts controls",
                onClick = onNavigateToPrivacy
            )

            SettingsClickableRow(
                icon = Icons.Default.Block,
                title = "Blocked Users",
                subtitle = "Manage blocked accounts",
                onClick = onNavigateToBlockedUsers
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NOTIFICATIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )

            SettingsSwitchRow(
                icon = Icons.Default.Notifications,
                title = "Allow Notifications",
                subtitle = "Enable or disable all notifications",
                checked = notificationsEnabled,
                onCheckedChange = { settingsViewModel.toggleNotifications(it) }
            )

            if (notificationsEnabled) {
                SettingsSwitchRow(
                    icon = Icons.Default.ChatBubble,
                    title = "Message Notifications",
                    subtitle = "Receive popups for incoming chat messages",
                    checked = messageNotificationsEnabled,
                    onCheckedChange = { settingsViewModel.toggleMessageNotifications(it) }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Notification Sound",
                    subtitle = "Play sound when receiving new messages",
                    checked = notificationSoundEnabled,
                    onCheckedChange = { settingsViewModel.toggleNotificationSound(it) }
                )

                SettingsSwitchRow(
                    icon = Icons.Default.Vibration,
                    title = "Vibration",
                    subtitle = "Vibrate device on incoming message alerts",
                    checked = vibrationEnabled,
                    onCheckedChange = { settingsViewModel.toggleVibration(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // Logout row
            SettingsClickableRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Log Out",
                subtitle = "Sign out of your Lala Chat account",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = {
                    authViewModel.logout()
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPrimary)
        )
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (titleColor != androidx.compose.ui.graphics.Color.Unspecified) titleColor else EmeraldPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
