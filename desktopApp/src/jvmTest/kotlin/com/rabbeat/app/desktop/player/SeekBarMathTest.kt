package com.rabbeat.app.desktop.player

import com.rabbeat.app.desktop.ui.seekFractionForX
import kotlin.test.Test
import kotlin.test.assertEquals

class SeekBarMathTest {
    @Test
    fun xToFractionBoundaries() {
        assertEquals(0f, seekFractionForX(0f, 200f))
        assertEquals(1f, seekFractionForX(200f, 200f))
        assertEquals(0.5f, seekFractionForX(100f, 200f))
        assertEquals(0.25f, seekFractionForX(50f, 200f))
    }

    @Test
    fun outOfRangeClamps() {
        assertEquals(0f, seekFractionForX(-30f, 200f))
        assertEquals(1f, seekFractionForX(500f, 200f))
    }

    @Test
    fun zeroWidthIsZero() {
        assertEquals(0f, seekFractionForX(50f, 0f))
        assertEquals(0f, seekFractionForX(50f, -10f))
    }
}
