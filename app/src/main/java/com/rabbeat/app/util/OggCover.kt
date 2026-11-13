package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.InputStream

/**
 * OGG Vorbis stores cover art inside the comment header packet as a
 * `METADATA_BLOCK_PICTURE` (base64 FLAC picture block) or a legacy raw
 * `COVERART` base64 field. `MediaMetadataRetriever.embeddedPicture` often
 * misses these, so the comment packet is parsed directly.
 */
object OggCover {

    private const val MAX_PAGES = 64
    private const val MAX_PACKET = 4 shl 20

    fun read(context: Context, uri: String): ByteArray? =
        runCatching {
            val parsed = Uri.parse(uri)
            if (parsed.scheme == "file" || parsed.scheme == null) {
                File(parsed.path ?: return null).inputStream()
            } else {
                context.contentResolver.openInputStream(parsed)
            }?.use { extract(it) }
        }.getOrNull()

    private fun extract(input: InputStream): ByteArray? {
        val header = ByteArray(27)
        var packet = ByteArray(0)
        var pages = 0
        while (pages++ < MAX_PAGES) {
            if (!fill(input, header)) return null
            if (header[0] != 'O'.code.toByte() || header[1] != 'g'.code.toByte() ||
                header[2] != 'g'.code.toByte() || header[3] != 'S'.code.toByte()
            ) return null
            val continuing = header[5].toInt() and 1 != 0
            if (!continuing) packet = ByteArray(0)
            val segments = header[26].toInt() and 0xFF
            val lacing = ByteArray(segments)
            if (!fill(input, lacing)) return null
            for (seg in lacing) {
                val size = seg.toInt() and 0xFF
                if (size > 0) {
                    if (packet.size + size > MAX_PACKET) return null
                    val chunk = ByteArray(size)
                    if (!fill(input, chunk)) return null
                    packet += chunk
                }
                if (size < 255) {
                    val tagAt = tagOffset(packet)
                    if (tagAt >= 0) return picture(packet, tagAt)
                    packet = ByteArray(0)
                }
            }
        }
        return null
    }

    /** Vorbis comment packet (`\x03vorbis`) or Opus tags packet (`OpusTags`). */
    private fun tagOffset(packet: ByteArray): Int = when {
        packet.size >= 7 && packet[0] == 3.toByte() &&
            packet.copyOfRange(1, 7).contentEquals("vorbis".toByteArray()) -> 7
        packet.size >= 8 && packet.copyOfRange(0, 8).contentEquals("OpusTags".toByteArray()) -> 8
        else -> -1
    }

    /** Parse a tag packet (positioned past its signature) and decode the first picture found. */
    private fun picture(packet: ByteArray, tagAt: Int): ByteArray? {
        var pos = tagAt
        fun u32(): Int? {
            if (pos + 4 > packet.size) return null
            val v = le(packet, pos)
            pos += 4
            return v
        }
        val vendor = u32() ?: return null
        if (vendor < 0 || pos + vendor > packet.size) return null
        pos += vendor
        if (pos + 4 > packet.size) return null
        val count = u32() ?: return null
        var art: ByteArray? = null
        repeat(count.coerceAtMost(512)) {
            val len = u32() ?: return null
            if (pos + len > packet.size) return null
            val entry = String(packet, pos, len, Charsets.UTF_8)
            pos += len
            val sep = entry.indexOf('=')
            if (sep <= 0) return@repeat
            when (entry.substring(0, sep).uppercase()) {
                "METADATA_BLOCK_PICTURE" ->
                    runCatching { Base64.decode(entry.substring(sep + 1), Base64.DEFAULT) }
                        .getOrNull()
                        ?.let { return flacPicture(it) }
                "COVERART" ->
                    runCatching { Base64.decode(entry.substring(sep + 1), Base64.DEFAULT) }
                        .getOrNull()
                        ?.let { art = it }
            }
        }
        return art
    }

    /** FLAC picture block: type(4) mimeLen(4) mime descLen(4) desc w h depth colors dataLen(4) data. */
    private fun flacPicture(block: ByteArray): ByteArray? {
        if (block.size < 32) return null
        var pos = 0
        fun u32(): Int {
            val v = be(block, pos)
            pos += 4
            return v
        }
        pos += 4 // picture type
        pos += u32() // mime
        pos += u32() // description
        pos += 16 // width height depth colors
        if (pos < 0 || pos + 4 > block.size) return null
        val len = u32()
        if (pos < 0 || pos + len > block.size || len <= 0) return null
        return block.copyOfRange(pos, pos + len)
    }

    private fun fill(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun be(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or
            (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or
            (b[o + 3].toInt() and 0xFF)
}
