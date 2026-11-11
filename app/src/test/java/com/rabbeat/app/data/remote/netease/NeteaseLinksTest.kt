package com.rabbeat.app.data.remote.netease

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseLinksTest {
    @Test
    fun plainDigits() {
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId("5337626002"))
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId("  5337626002 "))
    }

    @Test
    fun desktopUrlWithParams() {
        assertEquals(
            5337626002L,
            NeteaseLinks.parsePlaylistId(
                "https://music.163.com/playlist?id=5337626002&uct2=U2FsdGVkX1/iZwdC+XRvjkmX3Dr1W213TU54LZ/yQqM=",
            ),
        )
    }

    @Test
    fun hashRouteUrl() {
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId("https://music.163.com/#/playlist?id=5337626002"))
    }

    @Test
    fun mobileUrl() {
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId("https://y.music.163.com/m/playlist?id=5337626002"))
    }

    @Test
    fun shareTextWithChinese() {
        val text = "分享测试歌单《我喜欢的音乐》: https://music.163.com/playlist?id=5337626002&userid=42 (来自@网易云音乐)"
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId(text))
    }

    @Test
    fun playlistPathUrl() {
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId("https://music.163.com/playlist/5337626002"))
    }

    @Test
    fun bareIdParam() {
        assertEquals(5337626002L, NeteaseLinks.parsePlaylistId("something&id=5337626002"))
    }

    @Test
    fun garbageReturnsNull() {
        assertNull(NeteaseLinks.parsePlaylistId("hello world"))
        assertNull(NeteaseLinks.parsePlaylistId("https://music.163.com/song/12345"))
        assertNull(NeteaseLinks.parsePlaylistId(""))
    }

    @Test
    fun shortLinkNeedsRedirect() {
        val text = "https://163cn.tv/AbCdEf 点我"
        assertNull(NeteaseLinks.parsePlaylistId(text))
        assertEquals("https://163cn.tv/AbCdEf", NeteaseLinks.findShortLink(text))
        assertNull(NeteaseLinks.findShortLink("https://music.163.com/playlist?id=1"))
    }
}
