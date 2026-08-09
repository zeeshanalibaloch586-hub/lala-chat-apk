package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.auth.ForgotPasswordScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SignUpScreen
import com.example.ui.screens.chat.ChatDetailScreen
import com.example.ui.screens.home.ChatListScreen
import com.example.ui.screens.lock.AppLockOverlay
import com.example.ui.screens.profile.EditProfileScreen
import com.example.ui.screens.profile.UserProfileScreen
import com.example.ui.screens.settings.BlockedUsersScreen
import com.example.ui.screens.settings.PrivacySettingsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.LalaTheme
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private var targetChatIdState = mutableStateOf<String?>(null)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermission()
        handleIntent(intent)

        setContent {
            LalaApp(targetChatIdState.value) {
                targetChatIdState.value = null
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val chatId = intent?.getStringExtra("target_chat_id")
        if (!chatId.isNullOrEmpty()) {
            targetChatIdState.value = chatId
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun LalaApp(
    targetChatId: String? = null,
    onTargetChatHandled: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = viewModel()
    val chatViewModel: ChatViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val currentUser by authViewModel.currentUser.collectAsState()
    val isDarkTheme by settingsViewModel.isDarkTheme.collectAsState()
    val isAppLocked by settingsViewModel.isAppLocked.collectAsState()

    val navController = rememberNavController()

    // Handle target chat navigation from notification tap
    LaunchedEffect(targetChatId, currentUser) {
        if (currentUser != null && !targetChatId.isNullOrEmpty()) {
            chatViewModel.openChatById(targetChatId)
            navController.navigate("chat_detail") {
                launchSingleTop = true
            }
            onTargetChatHandled()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUser) {
        val observer = LifecycleEventObserver { _, event ->
            if (currentUser != null) {
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                        authViewModel.setOnlineStatus(true)
                    }
                    Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> {
                        authViewModel.setOnlineStatus(false)
                    }
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LalaTheme(darkTheme = isDarkTheme) {
        if (isAppLocked && currentUser != null) {
            AppLockOverlay(
                settingsViewModel = settingsViewModel,
                onUnlocked = { settingsViewModel.unlockApp() }
            )
        } else {
            val startDestination = if (currentUser != null) "chat_list" else "login"

            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                composable("login") {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onNavigateToSignUp = { navController.navigate("signup") },
                        onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                        onLoginSuccess = { navController.navigate("chat_list") { popUpTo("login") { inclusive = true } } }
                    )
                }

                composable("signup") {
                    SignUpScreen(
                        authViewModel = authViewModel,
                        onNavigateToLogin = { navController.popBackStack() }
                    )
                }

                composable("forgot_password") {
                    ForgotPasswordScreen(
                        authViewModel = authViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("chat_list") {
                    ChatListScreen(
                        authViewModel = authViewModel,
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel,
                        onOpenChat = { chat ->
                            chatViewModel.openChatById(chat.chatId)
                            navController.navigate("chat_detail")
                        },
                        onNavigateToProfile = { navController.navigate("user_profile") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }

                composable("chat_detail") {
                    ChatDetailScreen(
                        chatViewModel = chatViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("user_profile") {
                    UserProfileScreen(
                        authViewModel = authViewModel,
                        onNavigateToEditProfile = { navController.navigate("edit_profile") },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("edit_profile") {
                    EditProfileScreen(
                        authViewModel = authViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("settings") {
                    SettingsScreen(
                        authViewModel = authViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateToPrivacy = { navController.navigate("privacy_settings") },
                        onNavigateToBlockedUsers = { navController.navigate("blocked_users") },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("privacy_settings") {
                    PrivacySettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onNavigateToBlockedUsers = { navController.navigate("blocked_users") },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable("blocked_users") {
                    BlockedUsersScreen(
                        chatViewModel = chatViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
