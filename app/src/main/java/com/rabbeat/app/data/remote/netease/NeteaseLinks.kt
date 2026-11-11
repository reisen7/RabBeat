package com.rabbeat.app.data.remote.netease

object NeteaseLinks {
    private val playlistId = Regex("""playlist[^0-9]{0,20}?id=(\d+)""")
    private val playlistPath = Regex("""playlist/(\d+)""")
    private val anyId = Regex("""[?&]id=(\d+)""")
    private val shortLink = Regex("""https?://163cn\.tv/\S+""")

    fun parsePlaylistId(text: String): Long? {
        val trimmed = text.trim()
        trimmed.toLongOrNull()?.let { return it }
        playlistId.find(trimmed)?.let { return it.groupValues[1].toLong() }
        playlistPath.find(trimmed)?.let { return it.groupValues[1].toLong() }
        anyId.find(trimmed)?.let { return it.groupValues[1].toLong() }
        return null
    }

    fun findShortLink(text: String): String? = shortLink.find(text)?.value
}
