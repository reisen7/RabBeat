package com.rabbeat.app.util

fun formatClock(ms: Long): String {
    val safe = ms.coerceAtLeast(0L) / 1000L
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    return when {
        value >= 1_000_000_000 -> "%.1f GB".format(value / 1_000_000_000)
        value >= 1_000_000 -> "%.1f MB".format(value / 1_000_000)
        value >= 1_000 -> "%.0f KB".format(value / 1_000)
        else -> "$bytes B"
    }
}
