package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.ui.theme.BubbleInDark
import com.example.ui.theme.BubbleInLight
import com.example.ui.theme.BubbleOutDark
import com.example.ui.theme.BubbleOutLight
import com.example.ui.theme.EmeraldPrimary
import com.example.util.TimeUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    onLongPress: (Message) -> Unit,
    onImageClick: (String) -> Unit,
    onReplyClick: ((String) -> Unit)? = null,
    onRetryClick: ((Message) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.red < 0.1f // AMOLED or Dark check

    val bubbleColor = when {
        isOutgoing -> if (isDark) BubbleOutDark else BubbleOutLight
        else -> if (isDark) BubbleInDark else BubbleInLight
    }

    val textColor = when {
        isOutgoing -> if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val mediaUrl = message.mediaUrl
    val replyToContent = message.replyToContent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 12.dp),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                shape = bubbleShape,
                modifier = Modifier
                    .clip(bubbleShape)
                    .combinedClickable(
                        onClick = {
                            if (message.type == MessageType.IMAGE && !mediaUrl.isNullOrEmpty()) {
                                onImageClick(mediaUrl)
                            }
                        },
                        onLongClick = { onLongPress(message) }
                    )
            ) {
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    // Reply quote block
                    if (!replyToContent.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                                .then(
                                    if (message.replyToMessageId != null && onReplyClick != null) {
                                        Modifier.clickable { onReplyClick(message.replyToMessageId) }
                                    } else Modifier
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(32.dp)
                                    .background(EmeraldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName ?: "Reply",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldPrimary
                                )
                                Text(
                                    text = replyToContent,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Forwarded tag
                    if (message.isForwarded) {
                        Text(
                            text = "Forwarded",
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    // Deleted message render
                    if (message.isDeletedForEveryone) {
                        Text(
                            text = "🚫 This message was deleted",
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    } else {
                        // Message Body based on type
                        when (message.type) {
                            MessageType.TEXT -> {
                                Text(
                                    text = message.content,
                                    fontSize = 15.sp,
                                    color = textColor,
                                    lineHeight = 20.sp
                                )
                            }
                            MessageType.IMAGE -> {
                                if (!mediaUrl.isNullOrEmpty()) {
                                    Box(contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(mediaUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Shared image",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )

                                        if (message.status == MessageStatus.SENDING) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(36.dp),
                                                    color = EmeraldPrimary,
                                                    strokeWidth = 3.dp
                                                )
                                            }
                                        }
                                    }
                                    if (message.content.isNotBlank() && message.content != "Photo") {
                                        Text(
                                            text = message.content,
                                            fontSize = 14.sp,
                                            color = textColor,
                                            modifier = Modifier.padding(top = 6.dp)
                                        )
                                    }
                                }
                            }
                            MessageType.FILE -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                                        .clickable {
                                            openFile(context, mediaUrl, message.mediaName)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "File",
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.mediaName ?: "Document.pdf",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = textColor,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (message.status == MessageStatus.SENDING) "Uploading file..." else "Attachment • Tap to open",
                                            fontSize = 11.sp,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }

                                    if (message.status == MessageStatus.SENDING) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = EmeraldPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            tint = textColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            MessageType.VOICE -> {
                                Box(contentAlignment = Alignment.CenterStart) {
                                    VoiceMessagePlayer(
                                        mediaUrl = mediaUrl,
                                        durationMs = message.voiceDurationMs,
                                        isOutgoing = isOutgoing
                                    )
                                    if (message.status == MessageStatus.SENDING) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = EmeraldPrimary,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Retry row if message sending failed
                    if (message.status == MessageStatus.FAILED && isOutgoing) {
                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { onRetryClick?.invoke(message) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Failed. Tap to retry",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Timestamp and Status Ticks
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = TimeUtils.formatMessageTime(message.timestamp),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.65f)
                        )

                        if (isOutgoing) {
                            StatusTicks(status = message.status)
                        }
                    }
                }
            }

            // Reactions row
            if (message.reactions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    message.reactions.values.distinct().take(4).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = emoji, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun openFile(context: Context, urlOrPath: String?, fileName: String?) {
    if (urlOrPath.isNullOrBlank()) return
    try {
        val extension = (fileName ?: urlOrPath).substringAfterLast('.', "").lowercase()
        val mimeType = when (extension) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "png", "jpg", "jpeg" -> "image/*"
            "mp3", "wav", "m4a", "aac" -> "audio/*"
            "mp4", "mkv" -> "video/*"
            "txt" -> "text/plain"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(urlOrPath), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open file with"))
    } catch (_: Exception) {
        Toast.makeText(context, "No application found to open file", Toast.LENGTH_SHORT).show()
    }
}
