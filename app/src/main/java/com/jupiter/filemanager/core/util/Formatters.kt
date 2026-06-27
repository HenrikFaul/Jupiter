package com.jupiter.filemanager.core.util

import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Formats a byte count into a human-readable string using base-1024 units.
 *
 * Examples: 0 -> "0 B", 1024 -> "1.0 KB", 1_500_000_000 -> "1.4 GB".
 * Zero and negative values are rendered as "0 B".
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    // Determine the unit index via log base 1024.
    val base = 1024.0
    val digitGroups = (ln(bytes.toDouble()) / ln(base)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / base.pow(digitGroups.toDouble())

    return if (digitGroups == 0) {
        // Whole bytes, no decimal point.
        "$bytes B"
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[digitGroups])
    }
}

/**
 * Formats an epoch-millis timestamp as a localized short date and time string.
 */
fun formatDate(epochMillis: Long): String {
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT)
    val date = Date(epochMillis)
    return dateFormat.format(date) + " " + timeFormat.format(date)
}

/**
 * Formats an epoch-millis timestamp relative to "now", e.g. "just now",
 * "5 minutes ago", "2 hours ago", "3 days ago". Falls back to an absolute
 * localized date for older timestamps. Future timestamps fall back to [formatDate].
 */
fun formatRelativeTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diffMillis = now - epochMillis

    if (diffMillis < 0L) {
        // Timestamp is in the future; show absolute date instead.
        return formatDate(epochMillis)
    }

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        seconds < 60L -> "just now"
        minutes < 60L -> "$minutes ${pluralize(minutes, "minute")} ago"
        hours < 24L -> "$hours ${pluralize(hours, "hour")} ago"
        days < 7L -> "$days ${pluralize(days, "day")} ago"
        else -> formatDate(epochMillis)
    }
}

/**
 * Formats an item count as "1 item" or "N items".
 */
fun formatItemCount(count: Int): String {
    return if (abs(count) == 1) "1 item" else "$count items"
}

private fun pluralize(amount: Long, unit: String): String {
    return if (amount == 1L) unit else "${unit}s"
}
