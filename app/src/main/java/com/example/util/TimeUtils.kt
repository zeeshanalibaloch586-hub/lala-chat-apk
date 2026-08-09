package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun formatMessageTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatChatHeaderTimestamp(timestamp: Long): String {
        val now = Calendar.getInstance()
        val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }

        return if (now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR)) {
            if (now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR)) {
                formatMessageTime(timestamp)
            } else if (now.get(Calendar.DAY_OF_YEAR) - msgTime.get(Calendar.DAY_OF_YEAR) == 1) {
                "Yesterday"
            } else {
                SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
            }
        } else {
            SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formatLastSeen(lastSeenTimestamp: Long, isOnline: Boolean): String {
        if (isOnline) return "Online"
        val diffMs = System.currentTimeMillis() - lastSeenTimestamp
        val diffMins = diffMs / (1000 * 60)
        val diffHours = diffMins / 60
        val diffDays = diffHours / 24

        return when {
            diffMins < 1 -> "Last seen just now"
            diffMins < 60 -> "Last seen $diffMins min ago"
            diffHours < 24 -> "Last seen $diffHours hr ago"
            diffDays == 1L -> "Last seen yesterday"
            else -> "Last seen " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSeenTimestamp))
        }
    }

    fun formatVoiceDuration(ms: Long): String {
        val totalSecs = ms / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs)
    }
}
