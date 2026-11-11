package com.rabbeat.app.data.remote.netease

import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.TrackOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseMapperTest {
    private fun song(
        id: Long = 42L,
        name: String = "Song",
        artists: List<String> = listOf("A"),
        album: String = "Alb",
        pic: String? = null,
        dt: Long = 1000L,
    ) = NeSong(
        id = id,
        name = name,
        ar = artists.map { NeArtist(it) },
        al = NeAlbum(album, pic),
        dt = dt,
    )

    @Test
    fun playableRequiresPositivePlAndNonNegativeSt() {
        assertTrue(NeteaseMapper.isPlayable(NePrivilege(1, pl = 128, st = 0)))
        assertFalse(NeteaseMapper.isPlayable(NePrivilege(1, pl = 0, st = 0)))
        assertFalse(NeteaseMapper.isPlayable(NePrivilege(1, pl = 128, st = -200)))
        assertFalse(NeteaseMapper.isPlayable(null))
    }

    @Test
    fun artistsJoinWithSlash() {
        assertEquals("A/B/C", NeteaseMapper.artistName(song(artists = listOf("A", "B", "C"))))
        assertEquals("未知艺术家", NeteaseMapper.artistName(song(artists = emptyList())))
    }

    @Test
    fun artworkIsHttpsWithParam() {
        assertEquals(
            "https://p1.music.126.net/x.jpg?param=300y300",
            NeteaseMapper.artworkUrl(song(pic = "http://p1.music.126.net/x.jpg")),
        )
        assertEquals(null, NeteaseMapper.artworkUrl(song(pic = null)))
    }

    @Test
    fun entityFields() {
        val entity = NeteaseMapper.toTrackEntity(song(id = 5, pic = "http://x/y.jpg"), now = 99L)
        assertEquals("ne_5", entity.id)
        assertEquals(NETEASE_SOURCE_ID, entity.sourceId)
        assertEquals("5", entity.remoteId)
        assertEquals("audio/mpeg", entity.mimeType)
        assertEquals(320000, entity.bitrate)
        assertEquals("https://music.163.com/song/media/outer/url?id=5.mp3", entity.uri)
        assertEquals("https://x/y.jpg?param=300y300", entity.artworkUri)
        assertEquals(TrackOrigin.REMOTE, entity.origin)
        assertEquals(DownloadState.NONE, entity.downloadState)
        assertEquals(NeteaseMapper.FOLDER, entity.folder)
        assertEquals(99L, entity.addedAt)
        assertTrue(entity.albumId?.startsWith("nealb_") == true)
        assertTrue(entity.artistId?.startsWith("neart_") == true)
    }
}
