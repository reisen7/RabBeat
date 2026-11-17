package com.rabbeat.app.desktop.player

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PcmPlaybackTest {
    @Test
    fun keeps48kHzInsteadOfForcing44100() {
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 16, 2, 4, 48000f, false)
        val pcm = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val out = toPlaybackPcm(AudioInputStream(ByteArrayInputStream(pcm), format, 2))
        assertEquals(48000f, out.format.sampleRate)
        assertEquals(16, out.format.sampleSizeInBits)
        assertEquals(2, out.format.channels)
        val read = ByteArray(8)
        assertEquals(8, out.read(read))
        assertTrue(read.contentEquals(pcm))
    }

    @Test
    fun converts24BitBigEndianTo16BitStereo() {
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 24, 2, 6, 44100f, true)
        // Full-scale positive left, full-scale negative right.
        val pcm = byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0x80.toByte(), 0x00, 0x00)
        val out = toPlaybackPcm(AudioInputStream(ByteArrayInputStream(pcm), format, 1))
        assertEquals(44100f, out.format.sampleRate)
        val read = ByteArray(4)
        assertEquals(4, out.read(read))
        assertEquals(0xFF, read[0].toInt() and 0xFF)
        assertEquals(0x7F, read[1].toInt() and 0xFF)
        assertEquals(0x00, read[2].toInt() and 0xFF)
        assertEquals(0x80, read[3].toInt() and 0xFF)
    }

    @Test
    fun duplicatesMono() {
        val format = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 1, 2, 44100f, false)
        val pcm = byteArrayOf(0x34, 0x12)
        val out = toPlaybackPcm(AudioInputStream(ByteArrayInputStream(pcm), format, 1))
        val read = ByteArray(4)
        assertEquals(4, out.read(read))
        assertTrue(read.contentEquals(byteArrayOf(0x34, 0x12, 0x34, 0x12)))
    }
}
