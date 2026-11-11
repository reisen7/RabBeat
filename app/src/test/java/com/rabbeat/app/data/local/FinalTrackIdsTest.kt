package com.rabbeat.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class FinalTrackIdsTest {

    @Test
    fun keptRowsKeepTheirIds() {
        val result = finalTrackIds(listOf("a", "b"), setOf("a", "b"), emptyMap())
        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun replacedRowsMapToWinner() {
        val result = finalTrackIds(
            listOf("a", "b"),
            setOf("a"),
            mapOf("b" to "winner1"),
        )
        assertEquals(listOf("a", "winner1"), result)
    }

    @Test
    fun unmappedRowsAreDropped() {
        val result = finalTrackIds(
            listOf("a", "b", "c"),
            setOf("a"),
            mapOf("c" to "w"),
        )
        assertEquals(listOf("a", "w"), result)
    }

    @Test
    fun duplicatesCollapseKeepingFirstPosition() {
        val result = finalTrackIds(
            listOf("a", "b", "c"),
            setOf("a", "b"),
            mapOf("c" to "a"),
        )
        assertEquals(listOf("a", "b"), result)
    }
}
