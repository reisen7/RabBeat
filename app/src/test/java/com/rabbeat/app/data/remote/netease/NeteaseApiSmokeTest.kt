package com.rabbeat.app.data.remote.netease

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class NeteaseApiSmokeTest {
    @Test
    @Ignore("hits the live NetEase API; run manually only")
    fun livePlaylistDetail() = runBlocking {
        val detail = NeteaseApi().playlist(5337626002L)
        assertNotNull(detail)
        assertTrue(detail!!.trackIds.isNotEmpty())
    }
}
