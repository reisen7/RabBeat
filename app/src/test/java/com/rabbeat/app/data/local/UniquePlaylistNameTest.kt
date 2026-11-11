package com.rabbeat.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class UniquePlaylistNameTest {
    @Test
    fun keepsFreeName() = assertEquals("ACG", uniquePlaylistName("ACG", setOf("我喜欢")))

    @Test
    fun appendsNewOnConflict() = assertEquals("ACG new", uniquePlaylistName("ACG", setOf("ACG")))

    @Test
    fun numbersFurtherConflicts() =
        assertEquals("ACG new3", uniquePlaylistName("ACG", setOf("ACG", "ACG new", "ACG new2")))
}
