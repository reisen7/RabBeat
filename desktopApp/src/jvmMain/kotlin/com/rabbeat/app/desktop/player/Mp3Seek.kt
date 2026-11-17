package com.rabbeat.app.desktop.player

import java.io.File
import java.io.RandomAccessFile

internal data class Mp3Point(
    val offset: Long,
    val atMs: Long,
    val endMs: Long,
    val reached: Boolean,
)

/**
 * Walks MP3 frame headers to the frame that contains [targetMs].
 * mp3spi's [javax.sound.sampled.AudioInputStream.skip] treats a PCM byte count as a
 * fraction of the compressed file, so seeking past roughly a quarter of a 320kbps
 * track lands past EOF and playback dies.
 */
internal object Mp3Seek {
    fun locate(file: File, targetMs: Long): Mp3Point? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            var pos = id3End(raf)
            var elapsed = 0.0
            val header = ByteArray(4)
            var frames = 0
            var lastOffset = pos
            var lastAt = 0L
            while (frames < MAX_FRAMES && pos + 4 <= raf.length()) {
                raf.seek(pos)
                if (raf.read(header) != 4) break
                val word = ((header[0].toInt() and 0xFF) shl 24) or
                    ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)
                val frame = decodeFrame(word) ?: break
                if (pos + frame.bytes > raf.length()) break
                if (elapsed >= targetMs) {
                    val at = elapsed.toLong()
                    return@use Mp3Point(pos, at, at, reached = true)
                }
                lastOffset = pos
                lastAt = elapsed.toLong()
                elapsed += frame.samples * 1000.0 / frame.sampleRate
                pos += frame.bytes
                frames++
            }
            if (frames == 0) null else Mp3Point(lastOffset, lastAt, elapsed.toLong(), reached = false)
        }
    }.getOrNull()

    private fun id3End(raf: RandomAccessFile): Long {
        val id3 = ByteArray(10)
        raf.seek(0)
        if (raf.read(id3) != 10) return 0
        if (id3[0] != 'I'.code.toByte() || id3[1] != 'D'.code.toByte() || id3[2] != '3'.code.toByte()) return 0
        val tagSize = ((id3[6].toInt() and 0x7F) shl 21) or
            ((id3[7].toInt() and 0x7F) shl 14) or
            ((id3[8].toInt() and 0x7F) shl 7) or
            (id3[9].toInt() and 0x7F)
        val footer = if (id3[5].toInt() and 0x10 != 0) 10 else 0
        return 10L + tagSize + footer
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
