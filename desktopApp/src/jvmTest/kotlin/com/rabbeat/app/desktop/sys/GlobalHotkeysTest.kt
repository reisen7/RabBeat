package com.rabbeat.app.desktop.sys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalHotkeysTest {
    @Test
    fun roundTripAndLabel() {
        val chord = Chord(ctrl = true, alt = true, shift = false, meta = false, vk = 0x20)
        assertEquals(chord, Chord.decode(chord.encode()))
        assertEquals("Ctrl + Alt + Space", chord.label())
    }

    @Test
    fun blankIsUnbound() {
        assertNull(Chord.decode(""))
        assertNull(Chord.decode(null))
    }

    @Test
    fun messageStructMatchesWindows() {
        assertEquals(48, GlobalHotkeys.messageBytes())
    }

    @Test
    fun hookAttaches() {
        GlobalHotkeys.install { }
        val deadline = System.currentTimeMillis() + 2000
        while (!GlobalHotkeys.isReady() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(GlobalHotkeys.isReady())
    }
}
