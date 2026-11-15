package com.rabbeat.app.domain.model

/** Rules applied by both the device scan and folder import. */
data class ScanSettings(
    val filterNonSongs: Boolean = true,
    val skipSmallFiles: Boolean = true,
    val minSizeKb: Int = 500,
    val skipShortTracks: Boolean = true,
    val minDurationSec: Int = 60,
) {
    val minSizeBytes: Long get() = minSizeKb.coerceAtLeast(1).toLong() * 1024L
    val minDurationMs: Long get() = minDurationSec.coerceAtLeast(1).toLong() * 1000L

    fun rejectsSize(size: Long): Boolean = skipSmallFiles && size > 0L && size < minSizeBytes

    fun rejectsDuration(durationMs: Long): Boolean = skipShortTracks && durationMs > 0L && durationMs < minDurationMs

    fun rejectsPath(path: String?): Boolean {
        if (!filterNonSongs || path.isNullOrBlank()) return false
        return path.split('/', '\\').any(::isNonSongFolder)
    }
}

fun isNonSongFolder(name: String): Boolean {
    val leaf = name.trim().trim('/').substringAfterLast('/')
    if (leaf.isEmpty()) return false
    return NON_SONG_FOLDERS.any { leaf.equals(it, ignoreCase = true) }
}

private val NON_SONG_FOLDERS = setOf(
    "ringtones",
    "ringtone",
    "notifications",
    "notification",
    "alarms",
    "alarm",
    "podcasts",
    "podcast",
    "audiobooks",
    "audiobook",
    "recordings",
    "recording",
    "call",
    "call recordings",
    "voicerecorder",
    "voice recorder",
    "录音",
    "通话录音",
    "铃声",
    "通知",
    "闹钟",
    "播客",
    "有声书",
    "语音备忘录",
)
