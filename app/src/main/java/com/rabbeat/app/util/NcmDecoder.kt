package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * Minimal Kotlin port of https://github.com/taurusxin/ncmdump.
 *
 * A NetEase `.ncm` container is a 8 byte header, an AES protected RC4 key, an AES
 * protected JSON tag block, the embedded cover and the RC4 encrypted audio. Decrypted
 * audio is kept in app storage so it survives rescans and can be played by ExoPlayer.
 */
@Singleton
class NcmDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val bitrate: Int?,
        val cover: ByteArray?,
    )

    data class Decoded(val file: File, val mimeType: String, val tags: Tags?)

    /**
     * Decrypts [source] into app storage. [cacheKey] should identify the source file
     * (uri id, size and modified time) so a rescan reuses the previous result.
     * Returns null when the stream is missing, unreadable or not an NCM file.
     */
    fun decode(source: Uri, cacheKey: String): Decoded? = runCatching {
        context.contentResolver.openInputStream(source)?.use { decode(it, cacheKey) }
    }.getOrNull()

    private fun decode(input: InputStream, cacheKey: String): Decoded? {
        val magic = ByteArray(MAGIC.size)
        readFully(input, magic)
        if (!magic.contentEquals(MAGIC)) return null
        skipFully(input, 2L)

        val keyLength = input.readIntLe()
        if (keyLength <= 0 || keyLength > MAX_KEY) return null
        val keyData = ByteArray(keyLength)
        readFully(input, keyData)
        for (i in keyData.indices) keyData[i] = (keyData[i].toInt() xor 0x64).toByte()
        val decryptedKey = aesEcbDecrypt(CORE_KEY, keyData)
        if (decryptedKey.size <= KEY_PREFIX_SIZE) return null
        val key = decryptedKey.copyOfRange(KEY_PREFIX_SIZE, decryptedKey.size)
        val keyBox = buildKeyBox(key)

        val metaLength = input.readIntLe()
        if (metaLength < 0 || metaLength > MAX_META) return null
        val tags = if (metaLength > 0) {
            val raw = ByteArray(metaLength)
            readFully(input, raw)
            runCatching { readTags(raw) }.getOrNull()
        } else {
            null
        }

        // CRC32 (4) + image version (1)
        skipFully(input, 5L)
        val coverFrameLength = input.readIntLe()
        val imageLength = input.readIntLe()
        if (imageLength < 0 || imageLength > MAX_COVER || coverFrameLength < imageLength) return null
        val cover = if (imageLength > 0) {
            ByteArray(imageLength).also { readFully(input, it) }
        } else {
            null
        }
        skipFully(input, (coverFrameLength - imageLength).toLong())

        val base = fileName(cacheKey)
        val cached = CACHE_EXTENSIONS
            .map { File(directory, "$base.$it") }
            .firstOrNull { it.isFile && it.length() > 0L }
        if (cached != null) return Decoded(cached, mimeOf(cached), tags?.withCover(cover))

        val temp = File.createTempFile("$base-", ".tmp", directory)
        var extension = "flac"
        try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(CHUNK)
                var first = true
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    decrypt(buffer, count, keyBox)
                    if (first) {
                        extension = if (isMp3(buffer, count)) "mp3" else "flac"
                        first = false
                    }
                    output.write(buffer, 0, count)
                }
            }
            if (temp.length() <= 0L) return null
            val target = File(directory, "$base.$extension")
            // rename() replaces atomically, so a concurrent scan never sees the file missing.
            if (!temp.renameTo(target) && target.length() <= 0L) {
                temp.copyTo(target, overwrite = true)
            }
        } finally {
            temp.delete()
        }
        val output = File(directory, "$base.$extension")
        if (!output.isFile || output.length() <= 0L) return null
        return Decoded(output, mimeOf(output), tags?.withCover(cover))
    }

    private fun Tags.withCover(cover: ByteArray?): Tags = copy(cover = cover)

    private fun readTags(raw: ByteArray): Tags? {
        for (i in raw.indices) raw[i] = (raw[i].toInt() xor 0x63).toByte()
        if (raw.size <= META_PREFIX_SIZE) return null
        val encoded = runCatching {
            String(raw, META_PREFIX_SIZE, raw.size - META_PREFIX_SIZE, Charsets.UTF_8)
        }.getOrNull() ?: return null
        val decrypted = aesEcbDecrypt(MODIFY_KEY, Base64.decode(encoded, Base64.DEFAULT))
        if (decrypted.size <= TAG_PREFIX_SIZE) return null
        val json = runCatching {
            String(decrypted, TAG_PREFIX_SIZE, decrypted.size - TAG_PREFIX_SIZE, Charsets.UTF_8)
        }.getOrNull() ?: return null
        val meta = runCatching { jsonFormat.decodeFromString(NcmMeta.serializer(), json) }.getOrNull() ?: return null
        val artist = meta.artist.orEmpty()
            .mapNotNull { entry -> (entry.firstOrNull() as? JsonPrimitive)?.content?.trim()?.ifBlank { null } }
            .joinToString("/")
        return Tags(
            title = meta.musicName?.trim()?.ifBlank { null },
            artist = artist.ifBlank { null },
            album = meta.album?.trim()?.ifBlank { null },
            durationMs = meta.duration?.takeIf { it >= 500L },
            bitrate = meta.bitrate?.takeIf { it > 0 },
            cover = null,
        )
    }

    private fun aesEcbDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val size = data.size / 16 * 16
        if (size == 0) return ByteArray(0)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        val output = cipher.doFinal(data, 0, size)
        val pad = output.last().toInt() and 0xff
        return if (pad in 1..16 && pad <= output.size) output.copyOf(output.size - pad) else output
    }

    private fun buildKeyBox(key: ByteArray): ByteArray {
        val box = ByteArray(256) { it.toByte() }
        var last = 0
        var offset = 0
        for (i in 0 until 256) {
            val swap = box[i].toInt() and 0xff
            val c = (swap + last + (key[offset].toInt() and 0xff)) and 0xff
            offset++
            if (offset >= key.size) offset = 0
            box[i] = box[c]
            box[c] = swap.toByte()
            last = c
        }
        return box
    }

    private fun decrypt(buffer: ByteArray, count: Int, keyBox: ByteArray) {
        for (i in 0 until count) {
            val j = (i + 1) and 0xff
            val first = keyBox[j].toInt() and 0xff
            val second = keyBox[(first + j) and 0xff].toInt() and 0xff
            val mask = keyBox[(first + second) and 0xff].toInt() and 0xff
            buffer[i] = (buffer[i].toInt() xor mask).toByte()
        }
    }

    private fun isMp3(buffer: ByteArray, count: Int): Boolean =
        count >= 3 &&
            buffer[0] == 'I'.code.toByte() &&
            buffer[1] == 'D'.code.toByte() &&
            buffer[2] == '3'.code.toByte()

    private fun mimeOf(file: File): String =
        if (file.extension.equals("mp3", ignoreCase = true)) "audio/mpeg" else "audio/flac"

    private val directory: File
        get() = File(context.noBackupFilesDir, DIRECTORY).apply { mkdirs() }

    private fun fileName(cacheKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cacheKey.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun InputStream.readIntLe(): Int {
        val b0 = read()
        val b1 = read()
        val b2 = read()
        val b3 = read()
        if (b3 < 0) throw EOFException("Unexpected end of NCM header")
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) throw EOFException("Unexpected end of NCM file")
            offset += count
        }
    }

    private fun skipFully(input: InputStream, count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            if (input.read() < 0) throw EOFException("Unexpected end of NCM file")
            left--
        }
    }

    @Serializable
    private data class NcmMeta(
        val musicName: String? = null,
        val album: String? = null,
        val artist: List<JsonArray>? = null,
        val duration: Long? = null,
        val bitrate: Int? = null,
        val format: String? = null,
    )

    companion object {
        private const val DIRECTORY = "ncm"
        private const val CHUNK = 0x8000
        private const val MAX_KEY = 1 shl 20
        private const val MAX_META = 1 shl 22
        private const val MAX_COVER = 1 shl 24
        private const val KEY_PREFIX_SIZE = 17
        private const val META_PREFIX_SIZE = 22
        private const val TAG_PREFIX_SIZE = 6
        private val CACHE_EXTENSIONS = listOf("mp3", "flac")
        private val MAGIC = "CTENFDAM".toByteArray(Charsets.US_ASCII)
        private val CORE_KEY = byteArrayOf(
            0x68, 0x7A, 0x48, 0x52, 0x41, 0x6D, 0x73, 0x6F,
            0x35, 0x6B, 0x49, 0x6E, 0x62, 0x61, 0x78, 0x57,
        )
        private val MODIFY_KEY = byteArrayOf(
            0x23, 0x31, 0x34, 0x6C, 0x6A, 0x6B, 0x5F, 0x21,
            0x5C, 0x5D, 0x26, 0x30, 0x55, 0x3C, 0x27, 0x28,
        )
        private val jsonFormat = Json { ignoreUnknownKeys = true }
    }
}
