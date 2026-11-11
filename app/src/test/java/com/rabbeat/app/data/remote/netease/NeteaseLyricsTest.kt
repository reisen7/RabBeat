package com.rabbeat.app.data.remote.netease

import com.rabbeat.app.util.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseLyricsTest {
    @Test
    fun mergePairsTranslations() {
        val merged = NeteaseLyrics.merge(
            "[00:01.00]第一句\n[00:02.00]第二句\n[00:04.50]第三句",
            "[00:01.00]first\n[00:04.50]third",
        )!!
        val lines = LrcParser.parse(merged)
        assertEquals(3, lines.size)
        assertEquals("第一句", lines[0].text)
        assertEquals("first", lines[0].translation)
        assertEquals("第二句", lines[1].text)
        assertNull(lines[1].translation)
        assertEquals("third", lines[2].translation)
        assertEquals(4500L, lines[2].timeMs)
    }

    @Test
    fun returnsNullWithoutMainLyric() {
        assertNull(NeteaseLyrics.merge(null, "[00:01.00]x"))
        assertNull(NeteaseLyrics.merge("", "[00:01.00]x"))
        assertNull(NeteaseLyrics.merge("plain text no timestamps", "[00:01.00]x"))
    }

    @Test
    fun passesThroughWhenNoTranslation() {
        val lrc = "[00:01.00]only"
        assertEquals(lrc, NeteaseLyrics.merge(lrc, null))
        assertEquals(lrc, NeteaseLyrics.merge(lrc, ""))
    }
}
