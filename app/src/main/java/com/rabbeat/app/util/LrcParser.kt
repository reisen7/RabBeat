package com.rabbeat.app.util

data class LrcLine(val timeMs: Long, val text: String, val translation: String? = null)

data class LyricSheet(
    val lines: List<LrcLine>,
    val synced: Boolean,
    val linked: Boolean,
) {
    companion object {
        val EMPTY = LyricSheet(emptyList(), synced = false, linked = false)
    }
}

object LrcParser {
    private val pattern = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun parse(raw: String): List<LrcLine> {
        val collected = mutableListOf<LrcLine>()
        raw.lineSequence().forEach { line ->
            val matches = pattern.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { match ->
                val time = timeOf(match) ?: return@forEach
                collected += LrcLine(time, text)
            }
        }
        return collected
            .groupBy { it.timeMs }
            .toSortedMap()
            .map { (time, lines) ->
                LrcLine(time, lines.first().text, lines.getOrNull(1)?.text)
            }
    }

    private fun timeOf(match: MatchResult): Long? {
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return minutes * 60_000L + seconds * 1_000L + fraction
    }
}
