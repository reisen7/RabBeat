package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun read(uriText: String): String? {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
        return runCatching { sidecar(uri) }.getOrNull()
            ?: runCatching { embedded(uri) }.getOrNull()
    }

    fun readText(uriText: String): String? {
        val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
        val bytes = when (uri.scheme) {
            "file" -> uri.path?.let { path -> runCatching { File(path).readBytes() }.getOrNull() }
            else -> runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        } ?: return null
        return decodeText(bytes).trim().ifBlank { null }
    }

    fun store(id: String, source: Uri): String? {
        val text = readText(source.toString()) ?: return null
        return storeText(id, text)
    }

    fun storeText(id: String, text: String): String? {
        val cleaned = text.trim().ifBlank { return null }
        val file = File(context.filesDir, "lyrics/$id.lrc")
        file.parentFile?.mkdirs()
        file.writeText(cleaned, Charsets.UTF_8)
        return Uri.fromFile(file).toString()
    }

    private fun sidecar(uri: Uri): String? {
        queryPath(uri)?.let { path ->
            val audio = File(path)
            val lrc = File(audio.parentFile, audio.nameWithoutExtension + ".lrc")
            if (lrc.isFile) {
                return runCatching { decodeText(lrc.readBytes()) }.getOrNull()
            }
        }
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val current = DocumentFile.fromSingleUri(context, uri) ?: return null
        val parent = current.parentFile ?: return null
        val base = current.name?.substringBeforeLast('.') ?: return null
        val match = parent.listFiles().firstOrNull { file ->
            file.name?.equals("$base.lrc", ignoreCase = true) == true
        } ?: return null
        return context.contentResolver.openInputStream(match.uri)?.use { decodeText(it.readBytes()) }
    }

    private fun queryPath(uri: Uri): String? {
        val cursor = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )
        }.getOrNull() ?: return null
        cursor.use { rows ->
            if (!rows.moveToFirst()) return null
            val index = rows.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (index < 0 || rows.isNull(index)) return null
            return rows.getString(index)?.takeIf { it.isNotBlank() }
        }
    }

    private fun embedded(uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { readUslt(it) }
    }

    private fun readUslt(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.read(header) != 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt() and 0xFF
        if (version != 3 && version != 4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = synchsafe(header, 6)
        if (tagSize <= 0 || tagSize > 8_000_000) return null
        val tag = ByteArray(tagSize)
        if (!readFully(input, tag)) return null
        var data = if (flags and 0x80 != 0) deunsync(tag) else tag
        var offset = 0
        if (flags and 0x40 != 0) {
            val extended = if (version == 4) synchsafe(data, 0) else int32(data, 0)
            if (extended <= 0 || extended > data.size) return null
            offset = extended
        }
        while (offset + 10 <= data.size) {
            val id = data.decodeToString(offset, offset + 4)
            if (id.isEmpty() || id[0] == '\u0000' || id[0] !in 'A'..'Z') break
            val frameSize = if (version == 4) synchsafe(data, offset + 4) else int32(data, offset + 4)
            offset += 10
            if (frameSize <= 0 || offset + frameSize > data.size) break
            if (id == "USLT") return decodeUslt(data.copyOfRange(offset, offset + frameSize))
            offset += frameSize
        }
        return null
    }

    private fun decodeUslt(frame: ByteArray): String? {
        if (frame.size < 5) return null
        val encoding = frame[0].toInt() and 0xFF
        var index = 4
        index = skipTerminated(frame, index, encoding)
        val text = decodeString(frame, index, frame.size, encoding).trim()
        return text.ifBlank { null }
    }

    private fun skipTerminated(bytes: ByteArray, start: Int, encoding: Int): Int {
        var index = start
        if (encoding == 1 || encoding == 2) {
            while (index + 1 < bytes.size) {
                if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) return index + 2
                index += 2
            }
            return bytes.size
        }
        while (index < bytes.size && bytes[index] != 0.toByte()) index++
        return (index + 1).coerceAtMost(bytes.size)
    }

    private fun decodeString(bytes: ByteArray, start: Int, end: Int, encoding: Int): String {
        if (start >= end || start >= bytes.size) return ""
        val slice = bytes.copyOfRange(start, end.coerceAtMost(bytes.size))
        val charset = when (encoding) {
            0 -> Charset.forName("ISO-8859-1")
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return runCatching { slice.toString(charset).trim { it <= ' ' || it == '\u0000' } }.getOrDefault("")
    }

    private fun deunsync(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        var index = 0
        while (index < bytes.size) {
            val current = bytes[index]
            out += current
            if (current == 0xFF.toByte() && index + 1 < bytes.size && bytes[index + 1] == 0.toByte()) {
                index += 2
            } else {
                index++
            }
        }
        return out.toByteArray()
    }

    private fun synchsafe(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun int32(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun decodeText(bytes: ByteArray): String {
        val utf = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        if (!utf.contains('\uFFFD')) return utf
        return runCatching { bytes.toString(Charset.forName("GBK")) }.getOrDefault(utf)
    }
}
