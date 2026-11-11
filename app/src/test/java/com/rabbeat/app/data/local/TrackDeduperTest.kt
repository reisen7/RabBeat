package com.rabbeat.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDeduperTest {
    private fun candidate(
        id: String,
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 100000L,
        fileSize: Long? = 1000L,
        mimeType: String? = "audio/mpeg",
        folder: String? = "Download",
    ) = DupCandidate(id, title, artist, album, durationMs, fileSize, mimeType, folder)

    @Test
    fun sameTrackInDifferentFoldersKeepsOne() {
        val a = candidate("a", title = "transfrontier feat. nachi", artist = "坂上なち", durationMs = 113842, fileSize = 4554752, folder = "Download")
        val b = candidate("b", title = "transfrontier feat. nachi", artist = "坂上なち", durationMs = 113842, fileSize = 4554752, folder = "Download/QQ")
        val result = TrackDeduper.dedupe(listOf(a, b), emptySet())
        assertEquals(1, result.keptIds.size)
        assertTrue(result.replaced.size == 1)
    }

    @Test
    fun sameNameSameDurationKeepsOne() {
        val a = candidate("a", title = "TABOOLESS", artist = "Alice", durationMs = 50000, fileSize = 100, folder = "Download")
        val b = candidate("b", title = "TABOOLESS", artist = "Alice", durationMs = 50000, fileSize = 100, folder = "Download/QQ")
        val result = TrackDeduper.dedupe(listOf(a, b), emptySet())
        assertEquals(1, result.keptIds.size)
    }

    @Test
    fun largerFileWins() {
        val small = candidate("small", title = "Over the Horizon", artist = "Samsung", durationMs = 198560, fileSize = 19936108, folder = "Music/Samsung")
        val large = candidate("large", title = "Over the Horizon", artist = "Samsung", durationMs = 198560, fileSize = 19948513, folder = "Music/Samsung")
        val result = TrackDeduper.dedupe(listOf(small, large), emptySet())
        assertEquals(setOf("large"), result.keptIds)
        assertEquals("large", result.replaced["small"])
    }

    @Test
    fun fullwidthAndCaseAndWhitespaceAreDuplicates() {
        val a = candidate("a", title = "ＴＡＢＯＯＬＥＳＳ", artist = "Alice", durationMs = 50000)
        val b = candidate("b", title = "tabooless ", artist = "alice", durationMs = 50000)
        assertTrue(TrackDeduper.isDuplicate(a, b))
        val c = candidate("c", title = "  tabooless   ", artist = "  Alice  ", durationMs = 50000)
        assertTrue(TrackDeduper.isDuplicate(a, c))
    }

    @Test
    fun durationWithinTwoSecondsIsDuplicate() {
        val a = candidate("a", durationMs = 100000)
        val b = candidate("b", durationMs = 101500)
        assertTrue(TrackDeduper.isDuplicate(a, b))
    }

    @Test
    fun durationFiveSecondsApartIsNotDuplicate() {
        val a = candidate("a", durationMs = 100000)
        val b = candidate("b", durationMs = 105000)
        assertFalse(TrackDeduper.isDuplicate(a, b))
    }

    @Test
    fun sameTitleDifferentArtistIsNotDuplicate() {
        val a = candidate("a", artist = "Alice")
        val b = candidate("b", artist = "Bob")
        assertFalse(TrackDeduper.isDuplicate(a, b))
    }

    @Test
    fun losslessBeatsLargerMp3() {
        val mp3 = candidate("mp3", fileSize = 20_000_000, mimeType = "audio/mpeg")
        val flac = candidate("flac", fileSize = 10_000_000, mimeType = "audio/flac")
        val result = TrackDeduper.dedupe(listOf(mp3, flac), emptySet())
        assertEquals(setOf("flac"), result.keptIds)
        assertEquals("flac", result.replaced["mp3"])
    }

    @Test
    fun existingTrackWinsOnTie() {
        val a = candidate("a", fileSize = 1000)
        val b = candidate("b", fileSize = 1000)
        val result = TrackDeduper.dedupe(listOf(a, b), setOf("b"))
        assertEquals(setOf("b"), result.keptIds)
        assertEquals("b", result.replaced["a"])
    }

    @Test
    fun planResolvesChainedReplacementToOtherSource() {
        val a = candidate("a", title = "Song", artist = "Artist", durationMs = 100000, folder = "Download")
        val b = candidate("b", title = "song", artist = "artist", durationMs = 100000, folder = "Download/QQ")
        val x = candidate("x", title = "Song", artist = "Artist", durationMs = 100000, folder = "fld_other")
        val plan = TrackDeduper.plan(listOf(a, b), setOf("a", "b"), listOf(x))
        assertEquals(emptySet<String>(), plan.keptIds)
        assertEquals("x", plan.replaced["a"])
        assertEquals("x", plan.replaced["b"])
    }

    @Test
    fun planPointsLosersAtKeptWinner() {
        val a = candidate("a", folder = "Download")
        val b = candidate("b", folder = "Download/QQ")
        val plan = TrackDeduper.plan(listOf(a, b), emptySet(), emptyList())
        assertEquals(setOf("a"), plan.keptIds)
        assertEquals("a", plan.replaced["b"])
    }

    @Test
    fun zeroDurationFallsBackToAlbum() {
        val a = candidate("a", durationMs = 0, album = "Same Album")
        val b = candidate("b", durationMs = 0, album = "same album")
        assertTrue(TrackDeduper.isDuplicate(a, b))
        val c = candidate("c", durationMs = 0, album = "Other")
        assertFalse(TrackDeduper.isDuplicate(a, c))
    }
}
