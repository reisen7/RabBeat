package com.rabbeat.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderTest {

    @Test
    fun simpleMoveAppliesVisibleOrder() {
        val existing = listOf("a", "b", "c", "d")
        val result = reorderedPositions(existing, listOf("a", "c", "b", "d"))
        assertEquals(listOf("a", "c", "b", "d"), result)
    }

    @Test
    fun hiddenRefsAreAppendedInOriginalRelativeOrder() {
        val existing = listOf("a", "b", "c", "d", "e")
        val result = reorderedPositions(existing, listOf("d", "a"))
        assertEquals(listOf("d", "a", "b", "c", "e"), result)
    }

    @Test
    fun idsNotInPlaylistAreIgnored() {
        val existing = listOf("a", "b")
        val result = reorderedPositions(existing, listOf("b", "x", "a"))
        assertEquals(listOf("b", "a"), result)
    }

    @Test
    fun emptyPlaylistStaysEmpty() {
        assertEquals(emptyList<String>(), reorderedPositions(emptyList(), listOf("a", "b")))
    }
}
