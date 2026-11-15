package com.rabbeat.app.desktop.player
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import java.io.File
import java.net.URI
import java.util.regex.Pattern

/** Sidecar `.lrc` reader — looks for `<audio basename>.lrc` next to the file. */
object LyricLoader {
    private val LINE = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)](.*)")

    data class LrcLine(val atMs: Long, val text: String)

    fun loadFor(track: com.rabbeat.app.domain.model.Track): List<LrcLine> {
        val audio = runCatching { File(URI(track.uri)) }.getOrNull() ?: return emptyList()
        val lrc = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
        if (!lrc.isFile) return emptyList()
        return parse(lrc.readText())
    }

    fun parse(content: String): List<LrcLine> =
        content.lineSequence()
            .flatMap { line ->
                val out = mutableListOf<LrcLine>()
                val matcher = LINE.matcher(line)
                var lastText = ""
                while (matcher.find()) {
                    val min = matcher.group(1).toLong()
                    val sec = matcher.group(2).toDouble()
                    lastText = matcher.group(3).trim()
                    out += LrcLine((min * 60_000 + sec * 1000).toLong(), lastText)
                }
                out.asSequence()
            }
            .filter { it.text.isNotEmpty() }
            .sortedBy { it.atMs }
            .toList()

    fun currentIndex(lines: List<LrcLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].atMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
