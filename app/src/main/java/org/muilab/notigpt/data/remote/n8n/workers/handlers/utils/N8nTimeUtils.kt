package org.muilab.notigpt.data.remote.n8n.workers.handlers.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object N8nTimeUtils {

    fun isoTime(timeMs: Long): String {
        return SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            Locale.getDefault()
        ).format(Date(timeMs))
    }

    fun relativeTime(timeMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = nowMs - timeMs
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            seconds >= 0 -> "${seconds}s ago"
            else -> "0s ago"
        }
    }
}

