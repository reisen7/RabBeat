package com.rabbeat.app.desktop.player
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import java.io.File
import java.net.URI
import java.util.regex.Pattern

/** Sidecar `.lrc` next to a local file, or `<cache>/lyrics/<trackId>.lrc` for remote tracks. */
object LyricLoader {
    private val LINE = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)](.*)")

    data class LrcLine(val atMs: Long, val text: String)

    fun loadFor(track: com.rabbeat.app.domain.model.Track): List<LrcLine> {
        val file = sidecar(track)?.takeIf { it.isFile } ?: storedFile(track.id).takeIf { it.isFile } ?: return emptyList()
        return runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrDefault(emptyList())
    }

    /** Writes lyrics where [loadFor] will find them. Local files keep a sidecar; remote tracks go to the lyrics dir. */
    fun save(track: com.rabbeat.app.domain.model.Track, lrc: String): File? {
        val target = sidecar(track)?.takeIf { it.parentFile?.isDirectory == true } ?: storedFile(track.id)
        return runCatching {
            target.parentFile?.mkdirs()
            target.writeText(lrc, Charsets.UTF_8)
            target
        }.getOrNull()
    }

    private fun sidecar(track: com.rabbeat.app.domain.model.Track): File? {
        if (!track.uri.startsWith("file:")) return null
        val audio = runCatching { File(URI(track.uri)) }.getOrNull() ?: return null
        val parent = audio.parentFile ?: return null
        return File(parent, audio.nameWithoutExtension + ".lrc")
    }

    private fun storedFile(id: String): File {
        val safe = id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "track" }
        return File(DesktopSettings().lyricRoot(), "$safe.lrc")
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
