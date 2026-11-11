package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Walks MP3 frames and ignores ID3 artwork. NetEase CloudMusic files often make
 * MediaStore and ExoPlayer report a longer duration, so the bar runs into silence.
 */
@Singleton
class Mp3DurationReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun readMillis(uri: String): Long? {
        cache[uri]?.let { return it }
        val measured = measure(uri) ?: return null
        if (measured < MIN_MS) return null
        cache[uri] = measured
        return measured
    }

    private fun measure(uriString: String): Long? {
        val uri = Uri.parse(uriString)
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.createInputStream().use { readStream(it) }
            } ?: context.contentResolver.openInputStream(uri)?.use { readStream(it) }
        }.getOrNull()
    }

    private fun readStream(raw: InputStream): Long? {
        val input = BufferedInputStream(raw, 64 * 1024)
        input.mark(16)
        val id3 = ByteArray(10)
        val read = input.read(id3)
        if (read == 10 && id3[0] == 'I'.code.toByte() && id3[1] == 'D'.code.toByte() && id3[2] == '3'.code.toByte()) {
            val tagSize = synchsafe(id3[6], id3[7], id3[8], id3[9]).toLong()
            val footer = if (id3[5].toInt() and 0x10 != 0) 10L else 0L
            if (!skipFully(input, tagSize + footer)) return null
        } else {
            input.reset()
        }
        var millis = 0.0
        var frames = 0
        val header = ByteArray(4)
        while (frames < MAX_FRAMES && readFully(input, header)) {
            val word = ((header[0].toInt() and 0xFF) shl 24) or
                ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            val frame = decodeFrame(word) ?: break
            if (!skipFully(input, (frame.bytes - 4).toLong())) break
            millis += frame.samples * 1000.0 / frame.sampleRate
            frames++
        }
        if (frames < 2) return null
        return millis.roundToLong()
    }

    private data class Frame(val bytes: Int, val samples: Int, val sampleRate: Int)

    private fun decodeFrame(word: Int): Frame? {
        if (word ushr 21 != 0x7FF) return null
        val version = (word ushr 19) and 0x3
        val layer = (word ushr 17) and 0x3
        val bitrateIndex = (word ushr 12) and 0xF
        val sampleIndex = (word ushr 10) and 0x3
        val padding = (word ushr 9) and 0x1
        if (version == 1 || layer == 0 || bitrateIndex == 0 || bitrateIndex == 15 || sampleIndex == 3) return null
        val sampleRate = SAMPLE_RATES[version][sampleIndex]
        val bitrateKbps = BITRATES[if (version == 3) 0 else 1][3 - layer][bitrateIndex]
        if (bitrateKbps <= 0 || sampleRate <= 0) return null
        val samples = when (layer) {
            3 -> 384
            2 -> 1152
            else -> if (version == 3) 1152 else 576
        }
        val frameBytes = when (layer) {
            3 -> ((12 * bitrateKbps * 1000 / sampleRate) + padding) * 4
            1 -> {
                val scale = if (version == 3) 144 else 72
                (scale * bitrateKbps * 1000 / sampleRate) + padding
            }
            else -> (144 * bitrateKbps * 1000 / sampleRate) + padding
        }
        if (frameBytes < 4) return null
        return Frame(frameBytes, samples, sampleRate)
    }

    private fun synchsafe(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
            ((b1.toInt() and 0x7F) shl 14) or
            ((b2.toInt() and 0x7F) shl 7) or
            (b3.toInt() and 0x7F)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) return false
            offset += count
        }
        return true
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            if (input.read() < 0) return false
            left--
        }
        return true
    }

    companion object {
        private const val MIN_MS = 500L
        private const val MAX_FRAMES = 400_000
        private val SAMPLE_RATES = arrayOf(
            intArrayOf(11025, 12000, 8000),
            intArrayOf(0, 0, 0),
            intArrayOf(22050, 24000, 16000),
            intArrayOf(44100, 48000, 32000),
        )
        private val MPEG1 = arrayOf(
            intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0),
            intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0),
            intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0),
        )
        private val MPEG2 = arrayOf(
            intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0),
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
        )
        private val BITRATES = arrayOf(MPEG1, MPEG2)
    }
}
