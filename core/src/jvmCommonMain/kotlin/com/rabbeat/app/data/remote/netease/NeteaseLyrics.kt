package com.rabbeat.app.data.remote.netease

object NeteaseLyrics {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun merge(lrc: String?, tlyric: String?): String? {
        val main = extract(lrc)
        if (main.isEmpty()) return null
        val translation = extract(tlyric)
        if (translation.isEmpty()) return lrc
        val out = StringBuilder()
        main.forEach { (time, text) ->
            out.append(format(time)).append(text).append('\n')
            translation[time]?.let { out.append(format(time)).append(it).append('\n') }
        }
        return out.toString()
    }

    private fun extract(raw: String?): Map<Long, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val lines = linkedMapOf<Long, String>()
        raw.lineSequence().forEach { line ->
            val match = stamp.find(line) ?: return@forEach
            val text = line.substring(match.range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLong()
            lines.putIfAbsent(minutes * 60_000L + seconds * 1_000L + fraction, text)
        }
        return lines
    }

    private fun format(timeMs: Long): String {
        val minutes = timeMs / 60_000L
        val seconds = (timeMs % 60_000L) / 1_000L
        val hundredths = (timeMs % 1_000L) / 10L
        return "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
    }
}
