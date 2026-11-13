package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kotlin port of the QMC2 (embedded-key) decryption used by
 * https://github.com/mariozhaofan-pixel/mgg-to-mp3 (qmc2_core.py / qmc_reader.py).
 *
 * QQ Music `.mgg` / `.mflac` files carry a base64 ekey in a footer. The ekey is
 * unwrapped (optionally two Tencent-TEA layers) into a media key that drives a
 * Map-XOR (key <= 300 bytes) or segmented RC4 (> 300 bytes) stream cipher. Files
 * ending in "musicex" need an external key from the app database and cannot be
 * decrypted here; they are skipped.
 */
@Singleton
class QmcDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Decoded(val file: File, val mimeType: String)

    /**
     * Decrypts [source] into app storage, reusing a previous result when
     * [cacheKey] matches. Returns null for non-QMC, musicex (external-key) or
     * corrupt files.
     */
    fun decode(source: Uri, cacheKey: String): Decoded? = runCatching {
        val base = fileName(cacheKey)
        CACHE_EXTENSIONS
            .map { File(directory, "$base.$it") }
            .firstOrNull { it.isFile && it.length() > 0L }
            ?.let { return@runCatching Decoded(it, mimeOf(it.extension)) }
        openChannel(source)?.let { (channel, descriptor) ->
            try {
                decodeChannel(channel, base)
            } finally {
                runCatching { channel.close() }
                runCatching { descriptor?.close() }
            }
        }
    }.getOrNull()

    private fun openChannel(source: Uri): Pair<FileChannel, android.os.ParcelFileDescriptor?>? {
        val path = source.path
        if (source.scheme == "file" || source.scheme == null) {
            val file = File(path ?: return null)
            return if (file.isFile) file.inputStream().channel to null else null
        }
        val descriptor = context.contentResolver.openFileDescriptor(source, "r") ?: return null
        return java.io.FileInputStream(descriptor.fileDescriptor).channel to descriptor
    }

    private fun decodeChannel(channel: FileChannel, base: String): Decoded? {
        val size = channel.size()
        if (size < 16) return null

        // A header that is already plain audio means the file was never
        // encrypted; copy it through instead of trying to parse a footer.
        val head = ByteArray(minOf(64L, size).toInt())
        if (!readFully(channel, head, 0L)) return null
        if (detectFormat(head) != null) return decodePlain(channel, base, size)

        val tail = ByteArray(8)
        if (!readFully(channel, tail, size - 8)) return null
        if (tail.contentEquals(MUSICEX)) return null

        val qtag = tail.copyOfRange(4, 8).contentEquals(QTAG)
        val footerSize = if (qtag) readBe(tail, 0).toLong() else readLe(tail, 4).toLong()
        val footerEnd = if (qtag) size - 8 else size - 4
        if (footerSize <= 0L || footerSize > minOf(16384L, footerEnd)) {
            return decodePlain(channel, base, size)
        }
        val payloadEnd = footerEnd - footerSize
        if (payloadEnd < 16L || payloadEnd > Int.MAX_VALUE) return null

        var ekey = ByteArray(footerSize.toInt())
        if (!readFully(channel, ekey, payloadEnd)) return null
        if (qtag) ekey = ekey.copyOf(indexOf(ekey, ','.code.toByte()).let { if (it < 0) ekey.size else it })
        ekey = rstrip(ekey)
        if (ekey.isEmpty()) return null
        val key = deriveKey(ekey) ?: return null
        val rc4 = if (key.size > 300) Rc4Cipher(key) else null

        val temp = File.createTempFile("$base-", ".tmp", directory)
        var extension: String? = null
        try {
            temp.outputStream().use { output ->
                var offset = 0
                while (offset < payloadEnd) {
                    val want = minOf(CHUNK.toLong(), payloadEnd - offset).toInt()
                    val block = ByteArray(want)
                    if (!readFully(channel, block, offset.toLong())) return null
                    if (rc4 != null) rc4.decryptInto(block, offset) else mapDecrypt(block, key, offset)
                    if (offset == 0) {
                        extension = detectFormat(block) ?: return null
                    }
                    output.write(block)
                    offset += want
                }
            }
            if (temp.length() <= 0L) return null
            val target = File(directory, "$base.$extension")
            if (!temp.renameTo(target) && target.length() <= 0L) {
                temp.copyTo(target, overwrite = true)
            }
        } finally {
            temp.delete()
        }
        val out = File(directory, "$base.$extension")
        if (!out.isFile || out.length() <= 0L) return null
        return Decoded(out, mimeOf(extension!!))
    }

    /** The file was never encrypted: copy it through so playback sees a real format. */
    private fun decodePlain(channel: FileChannel, base: String, size: Long): Decoded? {
        if (size > Int.MAX_VALUE) return null
        val head = ByteArray(minOf(64L, size).toInt())
        if (!readFully(channel, head, 0L)) return null
        val extension = detectFormat(head) ?: return null
        val cached = File(directory, "$base.$extension")
        if (cached.isFile && cached.length() > 0L) return Decoded(cached, mimeOf(extension))
        val temp = File.createTempFile("$base-", ".tmp", directory)
        try {
            temp.outputStream().use { output ->
                var offset = 0L
                val buf = ByteArray(CHUNK)
                while (offset < size) {
                    val want = minOf(CHUNK.toLong(), size - offset).toInt()
                    if (!readFully(channel, buf, offset, want)) return null
                    output.write(buf, 0, want)
                    offset += want
                }
            }
            if (temp.length() <= 0L) return null
            if (!temp.renameTo(cached) && cached.length() <= 0L) {
                temp.copyTo(cached, overwrite = true)
            }
        } finally {
            temp.delete()
        }
        if (!cached.isFile || cached.length() <= 0L) return null
        return Decoded(cached, mimeOf(extension))
    }

    // ── Key derivation ──────────────────────────────────────────────

    private fun deriveKey(ekey: ByteArray): ByteArray? {
        var dec = base64Decode(ekey) ?: return null
        if (dec.size >= V2_PREFIX.size && dec.copyOfRange(0, V2_PREFIX.size).contentEquals(V2_PREFIX)) {
            val once = teaDecrypt(dec.copyOfRange(V2_PREFIX.size, dec.size), V2_KEY1) ?: return null
            val twice = teaDecrypt(once, V2_KEY2) ?: return null
            dec = base64Decode(twice) ?: return null
        }
        if (dec.size < 16) return null
        val teaKey = ByteArray(16)
        for (i in 0 until 8) {
            teaKey[2 * i] = SIMPLE_KEY[i]
            teaKey[2 * i + 1] = dec[i]
        }
        val body = teaDecrypt(dec.copyOfRange(8, dec.size), teaKey) ?: return dec
        return dec.copyOfRange(0, 8) + body
    }

    // ── Tencent-TEA (big-endian, 16 rounds, CBC variant) ────────────

    private fun teaBlockDec(block: ByteArray, k: IntArray) {
        var v0 = readBe(block, 0)
        var v1 = readBe(block, 4)
        var sum = 0xE3779B90.toInt()
        repeat(16) {
            v1 -= (v0 shl 4) + k[2] xor v0 + sum xor (v0 ushr 5) + k[3]
            v0 -= (v1 shl 4) + k[0] xor v1 + sum xor (v1 ushr 5) + k[1]
            sum -= 0x9E3779B9.toInt()
        }
        writeBe(block, 0, v0)
        writeBe(block, 4, v1)
    }

    private fun teaDecrypt(data: ByteArray, key: ByteArray): ByteArray? {
        if (data.size % 8 != 0 || data.size < 16) return null
        val tk = intArrayOf(readBe(key, 0), readBe(key, 4), readBe(key, 8), readBe(key, 12))
        val dest = data.copyOfRange(0, 8)
        teaBlockDec(dest, tk)
        val padLen = dest[0].toInt() and 0x07
        val outLen = data.size - 1 - padLen - 2 - 7
        if (outLen < 0) return null
        val out = ByteArray(outLen)
        var ivPrev = ByteArray(8)
        var ivCur = data.copyOfRange(0, 8)
        var inPos = 8
        var destIdx = 1 + padLen

        fun cryptBlock() {
            if (inPos + 8 > data.size) throw IndexOutOfBoundsException("tea: input exhausted")
            ivPrev = ivCur
            ivCur = data.copyOfRange(inPos, inPos + 8)
            for (i in 0 until 8) {
                dest[i] = (dest[i].toInt() xor data[inPos + i].toInt()).toByte()
            }
            teaBlockDec(dest, tk)
            inPos += 8
            destIdx = 0
        }

        var i = 1
        while (i <= 2) {
            if (destIdx < 8) {
                destIdx++
                i++
            } else {
                cryptBlock()
            }
        }
        var op = 0
        while (op < outLen) {
            if (destIdx < 8) {
                out[op] = (dest[destIdx].toInt() xor ivPrev[destIdx].toInt()).toByte()
                destIdx++
                op++
            } else {
                cryptBlock()
            }
        }
        var checked = 0
        while (checked < 7) {
            if (destIdx < 8) {
                if ((dest[destIdx].toInt() xor ivPrev[destIdx].toInt()) != 0) return null
                destIdx++
                checked++
            } else {
                cryptBlock()
            }
        }
        return out
    }

    // ── Map cipher (key <= 300 bytes) ───────────────────────────────

    private fun mapMask(key: ByteArray, offset: Int): Int {
        var off = offset
        if (off > 0x7FFF) off %= 0x7FFF
        val idx = (off * off + 71214) % key.size
        val v = key[idx].toInt() and 0xFF
        val r = ((idx and 7) + 4) % 8
        return if (r == 0) v else ((v shl r) or (v ushr r)) and 0xFF
    }

    private fun mapDecrypt(buf: ByteArray, key: ByteArray, offset: Int) {
        for (i in buf.indices) {
            buf[i] = (buf[i].toInt() xor mapMask(key, offset + i)).toByte()
        }
    }

    // ── Segmented pseudo-RC4 (key > 300 bytes) ──────────────────────

    private class Rc4Cipher(private val key: ByteArray) {
        private val n = key.size
        private val box = IntArray(n) { it and 0xFF }
        private val hash: Long

        init {
            var j = 0
            for (i in 0 until n) {
                j = (j + box[i] + (key[i % n].toInt() and 0xFF)) % n
                val t = box[i]; box[i] = box[j]; box[j] = t
            }
            var h = 1L
            for (i in 0 until n) {
                val v = key[i].toInt() and 0xFF
                if (v == 0) continue
                val next = (h * v) and 0xFFFFFFFFL
                if (next == 0L || next <= h) break
                h = next
            }
            hash = h
        }

        private fun segmentSkip(id: Int): Int {
            val seed = key[id % n].toInt() and 0xFF
            if (seed == 0) return 0
            return ((hash.toDouble() / ((id + 1).toDouble() * seed)) * 100.0).toInt() % n
        }

        private fun decryptSegmentInto(buf: ByteArray, out: Int, length: Int, offset: Int) {
            val b = box.copyOf()
            var j = 0
            var k = 0
            val skipLen = (offset % SEGMENT) + segmentSkip(offset / SEGMENT)
            var i = -skipLen
            while (i < length) {
                j = (j + 1) % n
                k = (b[j] + k) % n
                val t = b[j]; b[j] = b[k]; b[k] = t
                if (i >= 0) {
                    buf[out + i] = (buf[out + i].toInt() xor b[(b[j] + b[k]) % n]).toByte()
                }
                i++
            }
        }

        fun decryptInto(buf: ByteArray, off0: Int) {
            var offset = off0
            var remaining = buf.size
            var proc = 0
            if (offset < FIRST) {
                val bs = minOf(remaining, FIRST - offset)
                for (i in 0 until bs) {
                    buf[proc + i] = (buf[proc + i].toInt() xor
                        (key[segmentSkip(offset + i)].toInt() and 0xFF)).toByte()
                }
                offset += bs
                remaining -= bs
                proc += bs
                if (remaining == 0) return
            }
            if (offset % SEGMENT != 0) {
                val bs = minOf(remaining, SEGMENT - offset % SEGMENT)
                decryptSegmentInto(buf, proc, bs, offset)
                offset += bs
                remaining -= bs
                proc += bs
                if (remaining == 0) return
            }
            while (remaining > SEGMENT) {
                decryptSegmentInto(buf, proc, SEGMENT, offset)
                offset += SEGMENT
                remaining -= SEGMENT
                proc += SEGMENT
            }
            if (remaining > 0) decryptSegmentInto(buf, proc, remaining, offset)
        }

        companion object {
            private const val SEGMENT = 5120
            private const val FIRST = 128
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun detectFormat(b: ByteArray): String? = when {
        b.size >= 3 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte() -> "mp3"
        b.size >= 4 && b[0] == 'f'.code.toByte() && b[1] == 'L'.code.toByte() &&
            b[2] == 'a'.code.toByte() && b[3] == 'C'.code.toByte() -> "flac"
        b.size >= 4 && b[0] == 'O'.code.toByte() && b[1] == 'g'.code.toByte() &&
            b[2] == 'g'.code.toByte() && b[3] == 'S'.code.toByte() -> "ogg"
        b.size >= 8 && b[4] == 'f'.code.toByte() && b[5] == 't'.code.toByte() &&
            b[6] == 'y'.code.toByte() && b[7] == 'p'.code.toByte() -> "m4a"
        b.size >= 2 && b[0] == 0xFF.toByte() &&
            (b[1] == 0xF2.toByte() || b[1] == 0xF3.toByte() || b[1] == 0xFB.toByte()) -> "mp3"
        b.size >= 12 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() &&
            b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'A'.code.toByte() &&
            b[10] == 'V'.code.toByte() && b[11] == 'E'.code.toByte() -> "wav"
        else -> null
    }

    private fun mimeOf(extension: String): String = when (extension) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        else -> "audio/flac"
    }

    private fun base64Decode(data: ByteArray): ByteArray? {
        val padded = if (data.size % 4 != 0) {
            data + ByteArray(4 - data.size % 4) { '='.code.toByte() }
        } else {
            data
        }
        return runCatching { Base64.decode(padded, Base64.DEFAULT) }.getOrNull()
    }

    private fun indexOf(data: ByteArray, value: Byte): Int {
        for (i in data.indices) if (data[i] == value) return i
        return -1
    }

    private fun rstrip(data: ByteArray): ByteArray {
        var end = data.size
        while (end > 0 && (data[end - 1] == 0.toByte() || data[end - 1] == '\r'.code.toByte() ||
                data[end - 1] == '\n'.code.toByte() || data[end - 1] == ' '.code.toByte())) {
            end--
        }
        return data.copyOf(end)
    }

    private fun readFully(channel: FileChannel, buf: ByteArray, position: Long): Boolean =
        readFully(channel, buf, position, buf.size)

    private fun readFully(channel: FileChannel, buf: ByteArray, position: Long, count: Int): Boolean {
        val wrap = ByteBuffer.wrap(buf, 0, count)
        var pos = position
        while (wrap.hasRemaining()) {
            val n = channel.read(wrap, pos)
            if (n < 0) return false
            if (n == 0) continue
            pos += n
        }
        return true
    }

    private fun readBe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun readLe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or (b[o + 1].toInt() and 0xFF shl 8) or
            (b[o + 2].toInt() and 0xFF shl 16) or (b[o + 3].toInt() and 0xFF shl 24)

    private fun writeBe(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte()
        b[o + 1] = (v ushr 16).toByte()
        b[o + 2] = (v ushr 8).toByte()
        b[o + 3] = v.toByte()
    }

    private val directory: File
        get() = File(context.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    private fun fileName(cacheKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val DIRECTORY = "qmc"
        private const val CHUNK = 128 * 1024
        private val CACHE_EXTENSIONS = listOf("mp3", "flac", "ogg", "m4a", "wav")
        private val MUSICEX = "musicex\u0000".toByteArray(Charsets.US_ASCII)
        private val QTAG = "QTag".toByteArray(Charsets.US_ASCII)
        private val V2_PREFIX = "QQMusic EncV2,Key:".toByteArray(Charsets.US_ASCII)
        private val SIMPLE_KEY = byteArrayOf(
            0x69, 0x56, 0x46, 0x38, 0x2B, 0x20, 0x15, 0x0B,
        )
        private val V2_KEY1 = byteArrayOf(
            0x33, 0x38, 0x36, 0x5A, 0x4A, 0x59, 0x21, 0x40,
            0x23, 0x2A, 0x24, 0x25, 0x5E, 0x26, 0x29, 0x28,
        )
        private val V2_KEY2 = byteArrayOf(
            0x2A, 0x2A, 0x23, 0x21, 0x28, 0x23, 0x24, 0x25,
            0x26, 0x5E, 0x61, 0x31, 0x63, 0x5A, 0x2C, 0x54,
        )
        private val QMC_EXTENSIONS = setOf(
            "mgg", "mgg0", "mgg1", "mgg2", "mggl",
            "mflac", "mflac0", "mflac1", "mflac2",
            "qmcogg", "qmcflac",
        )

        fun isQmc(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in QMC_EXTENSIONS
    }
}
