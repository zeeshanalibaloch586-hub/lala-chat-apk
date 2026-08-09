package com.example.ui.screens.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Chat
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.LalaAvatar
import com.example.ui.components.MessageBubble
import com.example.ui.components.MessageContextMenu
import com.example.ui.components.TypingIndicator
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.ChatViewModel
import com.example.util.AudioRecorderPlayer
import com.example.util.NotificationHelper
import com.example.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

data class PendingMedia(
    val uri: Uri,
    val type: MessageType,
    val name: String? = null,
    val sizeBytes: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatViewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val activeChat by chatViewModel.activeChat.collectAsState()
    val messages by chatViewModel.messages.collectAsState()
    val currentUser by chatViewModel.currentUser.collectAsState()
    val replyToMessage by chatViewModel.replyToMessage.collectAsState()
    val typingMap by chatViewModel.typingStatusMap.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var showForwardDialog by remember { mutableStateOf<Message?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    var showAttachMenu by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    var pendingMediaPreview by remember { mutableStateOf<PendingMedia?>(null) }
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var voiceFile by remember { mutableStateOf<File?>(null) }

    val context = LocalContext.current
    val audioRecorder = remember { AudioRecorderPlayer(context) }
    val clipboardManager = LocalClipboardManager.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(activeChat?.chatId) {
        val chatId = activeChat?.chatId
        if (chatId != null) {
            NotificationHelper.activeChatId = chatId
        }
        onDispose {
            chatViewModel.sendTyping(false)
            if (NotificationHelper.activeChatId == chatId) {
                NotificationHelper.activeChatId = null
            }
        }
    }

    // Timer effect while recording voice
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            recordingDurationSec = 0
            while (isRecordingVoice) {
                delay(1000L)
                recordingDurationSec += 1
            }
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Media pickers & permissions
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            pendingMediaPreview = PendingMedia(
                uri = tempCameraUri!!,
                type = MessageType.IMAGE,
                name = "Photo_${System.currentTimeMillis()}.jpg"
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempImageUri(context)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = getFileNameAndSize(context, uri)
            pendingMediaPreview = PendingMedia(
                uri = uri,
                type = MessageType.IMAGE,
                name = name,
                sizeBytes = size
            )
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = getFileNameAndSize(context, uri)
            if (size > 50 * 1024 * 1024) {
                Toast.makeText(context, "File size exceeds 50MB limit", Toast.LENGTH_SHORT).show()
            } else {
                pendingMediaPreview = PendingMedia(
                    uri = uri,
                    type = MessageType.FILE,
                    name = name,
                    sizeBytes = size
                )
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val file = audioRecorder.startRecording()
            if (file != null) {
                voiceFile = file
                isRecordingVoice = true
                recordingDurationSec = 0
            } else {
                Toast.makeText(context, "Failed to start audio recording", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Audio permission is required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val isOpponentTyping = activeChat?.let { typingMap[it.chatId] == true } ?: false

    DisposableEffect(activeChat?.chatId) {
        onDispose {
            chatViewModel.sendTyping(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    activeChat?.let { chat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { /* Profile info */ }
                        ) {
                            LalaAvatar(
                                photoUrl = chat.otherUserPhoto,
                                name = chat.otherUserName,
                                size = 40.dp,
                                isOnline = chat.isOtherUserOnline
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = chat.otherUserName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isOpponentTyping) "typing..." else TimeUtils.formatLastSeen(chat.lastSeen, chat.isOtherUserOnline),
                                    fontSize = 11.sp,
                                    color = if (isOpponentTyping) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isOpponentTyping) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        chatViewModel.closeActiveChat()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Report User") },
                                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showReportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block User") },
                                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    activeChat?.let { chatViewModel.blockUser(it.otherUserId) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Conversation", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showTopMenu = false
                                    activeChat?.let {
                                        chatViewModel.deleteChat(it.chatId)
                                        onBack()
                                    }
                                }
                            )
                        }
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
                .imePadding()
        ) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom
            ) {
                items(messages, key = { it.messageId }) { msg ->
                    val isOutgoing = msg.senderId == currentUser?.userId
                    MessageBubble(
                        message = msg,
                        isOutgoing = isOutgoing,
                        onLongPress = { selectedMessageForMenu = it },
                        onImageClick = { url -> expandedImageUrl = url },
                        onReplyClick = { replyId ->
                            val targetIndex = messages.indexOfFirst { it.messageId == replyId }
                            if (targetIndex >= 0) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        },
                        onRetryClick = { messageToRetry ->
                            chatViewModel.retryMediaMessage(messageToRetry)
                        }
                    )
                }

                if (isOpponentTyping) {
                    item {
                        TypingIndicator(userName = activeChat?.otherUserName ?: "User")
                    }
                }
            }

            // Reply banner if active
            if (replyToMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to message",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                        Text(
                            text = replyToMessage!!.content,
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { chatViewModel.setReplyMessage(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Attachment drawer menu
            if (showAttachMenu) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachMenu = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                val uri = createTempImageUri(context)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = EmeraldPrimary)
                        }
                        Text("Camera", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachMenu = false
                            galleryLauncher.launch("image/*")
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Gallery", tint = EmeraldPrimary)
                        }
                        Text("Gallery", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            showAttachMenu = false
                            fileLauncher.launch("*/*")
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Description, contentDescription = "File", tint = EmeraldPrimary)
                        }
                        Text("File", fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            // Input Bar attached to keyboard
            if (isRecordingVoice) {
                // Active Voice Recording Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recording ${TimeUtils.formatVoiceDuration(recordingDurationSec * 1000L)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            audioRecorder.stopRecording()
                            isRecordingVoice = false
                            recordingDurationSec = 0
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel recording",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val file = audioRecorder.stopRecording()
                            val durationMs = recordingDurationSec * 1000L
                            isRecordingVoice = false
                            recordingDurationSec = 0
                            if (file != null) {
                                chatViewModel.sendMediaMessage(
                                    mediaUri = Uri.fromFile(file),
                                    type = MessageType.VOICE,
                                    mediaName = file.name,
                                    voiceDurationMs = if (durationMs > 0) durationMs else 1000L
                                )
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(EmeraldPrimary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send voice note",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = if (showAttachMenu) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            chatViewModel.sendTyping(it.isNotBlank())
                        },
                        placeholder = { Text("Message Lala Chat...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (inputText.isBlank()) {
                        // Voice note recorder button
                        IconButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val file = audioRecorder.startRecording()
                                    if (file != null) {
                                        voiceFile = file
                                        isRecordingVoice = true
                                        recordingDurationSec = 0
                                    } else {
                                        Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    EmeraldPrimary,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Message",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        // Send Button
                        IconButton(
                            onClick = {
                                val textToSend = inputText.trim()
                                if (textToSend.isNotBlank() && !isSendingMessage) {
                                    isSendingMessage = true
                                    chatViewModel.sendMessage(textToSend)
                                    inputText = ""
                                    chatViewModel.sendTyping(false)
                                    isSendingMessage = false
                                }
                            },
                            enabled = inputText.isNotBlank() && !isSendingMessage,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (inputText.isNotBlank() && !isSendingMessage) EmeraldPrimary else EmeraldPrimary.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // Media Preview Dialog before sending
    if (pendingMediaPreview != null) {
        val media = pendingMediaPreview!!
        var captionText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { pendingMediaPreview = null },
            title = {
                Text(
                    text = if (media.type == MessageType.IMAGE) "Send Photo" else "Send File",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (media.type == MessageType.IMAGE) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(media.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Photo Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "File",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = media.name ?: "File",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = formatFileSize(media.sizeBytes ?: 0L),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = captionText,
                        onValueChange = { captionText = it },
                        placeholder = { Text("Add a caption (optional)...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pMedia = media
                        pendingMediaPreview = null
                        chatViewModel.sendMediaMessage(
                            mediaUri = pMedia.uri,
                            type = pMedia.type,
                            mediaName = pMedia.name,
                            caption = captionText.trim()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMediaPreview = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Image Viewer Dialog
    if (expandedImageUrl != null) {
        val url = expandedImageUrl!!
        AlertDialog(
            onDismissRequest = { expandedImageUrl = null },
            title = null,
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Full Image Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { expandedImageUrl = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Long press context menu sheet
    if (selectedMessageForMenu != null) {
        val msg = selectedMessageForMenu!!
        val isOutgoing = msg.senderId == currentUser?.userId
        MessageContextMenu(
            message = msg,
            isOutgoing = isOutgoing,
            onReactionSelect = { emoji ->
                chatViewModel.toggleReaction(msg, emoji)
            },
            onReply = {
                chatViewModel.setReplyMessage(msg)
            },
            onCopy = {
                clipboardManager.setText(AnnotatedString(msg.content))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onForward = {
                showForwardDialog = msg
            },
            onDeleteForMe = {
                chatViewModel.deleteMessage(msg, deleteForEveryone = false)
            },
            onDeleteForEveryone = {
                chatViewModel.deleteMessage(msg, deleteForEveryone = true)
            },
            onDismiss = { selectedMessageForMenu = null }
        )
    }

    // Forward Dialog
    if (showForwardDialog != null) {
        val msgToForward = showForwardDialog!!
        val chatsList by chatViewModel.chats.collectAsState()

        AlertDialog(
            onDismissRequest = { showForwardDialog = null },
            title = { Text("Forward Message to") },
            text = {
                LazyColumn {
                    items(chatsList) { targetChat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatViewModel.forwardMessage(msgToForward, targetChat.chatId)
                                    showForwardDialog = null
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LalaAvatar(
                                photoUrl = targetChat.otherUserPhoto,
                                name = targetChat.otherUserName,
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = targetChat.otherUserName, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showForwardDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report User Dialog
    if (showReportDialog) {
        var reportDetails by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report User") },
            text = {
                Column {
                    Text("Help us keep Lala Chat safe. Please specify the issue:", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reportDetails,
                        onValueChange = { reportDetails = it },
                        label = { Text("Details (Spam, Harassment, etc.)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeChat?.let {
                            chatViewModel.reportUser(it.otherUserId, "User Report", reportDetails) {
                                showReportDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun createTempImageUri(context: Context): Uri {
    val tempFile = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        tempFile
    )
}

fun getFileNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
    var name = "Document"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "Document"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}
    return Pair(name, size)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}
