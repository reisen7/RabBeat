package com.rabbeat.app.desktop.player

import java.io.IOException
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Line format for [source]: same sample rate, 16-bit signed little-endian stereo.
 * Java Sound can change bit depth and endianness, but it cannot resample, so a
 * 48 kHz FLAC must stay 48 kHz or playback fails with "Unsupported conversion".
 */
internal fun playbackLineFormat(source: AudioFormat): AudioFormat {
    val rate = source.sampleRate.takeIf { it > 0f } ?: 44100f
    return AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        rate, 16, 2, 4, rate, false,
    )
}

/**
 * Drops [bytes] from [input]. jflac's decoded stream throws "skip not supported"
 * instead of returning 0, which used to abort resume-from-position.
 * Returns how many bytes could not be dropped.
 */
internal fun discardBytes(input: InputStream, bytes: Long): Long {
    var remaining = bytes
    val buf = ByteArray(64 * 1024)
    while (remaining > 0) {
        val skipped = try {
            input.skip(remaining)
        } catch (_: IOException) {
            0L
        }
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
        if (n <= 0) return remaining
        remaining -= n
    }
    return 0L
}

internal fun toPlaybackPcm(raw: AudioInputStream): AudioInputStream {
    // mp3spi only converts to a fully specified PCM format. Asking for the
    // PCM_SIGNED encoding alone throws "Unsupported conversion: PCM_SIGNED from MPEG…".
    // The rate stays the file's own rate: Java Sound cannot resample.
    val target = playbackLineFormat(raw.format)
    val decoded = when {
        raw.format.matches(target) -> raw
        else -> runCatching { AudioSystem.getAudioInputStream(target, raw) }.getOrNull()
            ?: runCatching { AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, raw) }.getOrNull()
            ?: raw
    }
    val line = playbackLineFormat(decoded.format)
    if (decoded.format.matches(line)) return decoded
    return runCatching { AudioSystem.getAudioInputStream(line, decoded) }
        .getOrElse { Pcm16StereoStream(decoded, line) }
}

private fun isLinearPcm(format: AudioFormat): Boolean {
    val name = format.encoding.toString()
    return format.encoding == AudioFormat.Encoding.PCM_SIGNED ||
        format.encoding == AudioFormat.Encoding.PCM_UNSIGNED ||
        name == "PCM_FLOAT"
}

/**
 * Converts PCM to 16-bit little-endian stereo without changing the sample rate.
 * Handles 8/16/24/32-bit integer and 32-bit float, either endian, mono or more channels.
 */
internal class Pcm16StereoStream(
    private val src: AudioInputStream,
    format: AudioFormat,
) : AudioInputStream(InputStream.nullInputStream(), format, src.frameLength) {

    private val srcChannels = src.format.channels.coerceAtLeast(1)
    private val srcBits = src.format.sampleSizeInBits.coerceAtLeast(8)
    private val srcBytes = srcBits / 8
    private val srcFrame = when {
        src.format.frameSize > 0 -> src.format.frameSize
        else -> srcBytes * srcChannels
    }
    private val bigEndian = src.format.isBigEndian
    private val unsigned = src.format.encoding == AudioFormat.Encoding.PCM_UNSIGNED
    private val float = src.format.encoding.toString() == "PCM_FLOAT"

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len < 4) return 0
        val frames = len / 4
        val raw = ByteArray(frames * srcFrame)
        var filled = 0
        while (filled < raw.size) {
            val n = src.read(raw, filled, raw.size - filled)
            if (n < 0) {
                if (filled == 0) return -1
                break
            }
            if (n == 0) break
            filled += n
        }
        val count = filled / srcFrame
        if (count == 0) return -1
        for (i in 0 until count) {
            val base = i * srcFrame
            val left = sampleAt(raw, base)
            val right = if (srcChannels >= 2) sampleAt(raw, base + srcBytes) else left
            writePair(b, off + i * 4, left, right)
        }
        return count * 4
    }

    override fun skip(n: Long): Long {
        var left = n - n % 4
        val buf = ByteArray(8192)
        var skipped = 0L
        while (left > 0) {
            val nRead = read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (nRead <= 0) break
            left -= nRead
            skipped += nRead
        }
        return skipped
    }

    override fun close() {
        src.close()
    }

    private fun sampleAt(buf: ByteArray, offset: Int): Int {
        if (float) {
            val bits = readBits(buf, offset, 4).toInt()
            val value = java.lang.Float.intBitsToFloat(bits).coerceIn(-1f, 1f)
            return (value * 32767f).toInt()
        }
        val wide = readBits(buf, offset, srcBytes)
        val signed = if (unsigned) {
            wide - (1L shl (srcBits - 1))
        } else {
            val sign = 1L shl (srcBits - 1)
            if (wide and sign != 0L) wide or (-1L shl srcBits) else wide
        }
        val scaled = if (srcBits >= 16) signed shr (srcBits - 16) else signed shl (16 - srcBits)
        return scaled.toInt().coerceIn(-32768, 32767)
    }

    private fun readBits(buf: ByteArray, offset: Int, width: Int): Long {
        var v = 0L
        if (bigEndian) {
            for (i in 0 until width) v = (v shl 8) or (buf[offset + i].toLong() and 0xff)
        } else {
            for (i in width - 1 downTo 0) v = (v shl 8) or (buf[offset + i].toLong() and 0xff)
        }
        return v
    }

    private fun writePair(buf: ByteArray, offset: Int, left: Int, right: Int) {
        buf[offset] = left.toByte()
        buf[offset + 1] = (left shr 8).toByte()
        buf[offset + 2] = right.toByte()
        buf[offset + 3] = (right shr 8).toByte()
    }
}
