package com.rabbeat.app.desktop.sys

/** mm:ss formatting shared by library, overlay and playlist views. */
fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
