package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.MessageStatus
import com.example.ui.theme.StatusTicksDelivered
import com.example.ui.theme.StatusTicksSeen

@Composable
fun StatusTicks(
    status: MessageStatus,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            MessageStatus.SENDING -> {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Sending",
                    tint = StatusTicksDelivered,
                    modifier = Modifier.size(14.dp)
                )
            }
            MessageStatus.SENT -> {
                // Single tick ✓
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Sent",
                    tint = StatusTicksDelivered,
                    modifier = Modifier.size(14.dp)
                )
            }
            MessageStatus.DELIVERED -> {
                // Double tick ✓✓
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Delivered",
                    tint = StatusTicksDelivered,
                    modifier = Modifier.size(15.dp)
                )
            }
            MessageStatus.SEEN -> {
                // Double tick emerald ✓✓
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = "Seen",
                    tint = StatusTicksSeen,
                    modifier = Modifier.size(15.dp)
                )
            }
            MessageStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
