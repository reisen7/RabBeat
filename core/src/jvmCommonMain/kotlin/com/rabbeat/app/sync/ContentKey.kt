package com.rabbeat.app.sync

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

/** Identifies the same audio on two devices without using local track ids. */
object ContentKey {
    const val CHUNK = 64 * 1024

    fun newSyncId(): String = UUID.randomUUID().toString().replace("-", "")

    fun netease(songId: String): String = "ne:$songId"

    fun sha1(text: String): String = digest(text.toByteArray(Charsets.UTF_8))

    fun of(file: File): String {
        RandomAccessFile(file, "r").use { raf ->
            val size = raf.length()
            val head = readAt(raf, 0, minOf(CHUNK.toLong(), size).toInt())
            val tailLen = minOf(CHUNK.toLong(), size).toInt()
            val tail = readAt(raf, maxOf(0L, size - tailLen), tailLen)
            return of(size, head, tail)
        }
    }

    fun of(size: Long, head: ByteArray, tail: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        val sizeBytes = ByteArray(8)
        var value = size
        for (index in 7 downTo 0) {
            sizeBytes[index] = (value and 0xff).toByte()
            value = value ushr 8
        }
        md.update(sizeBytes)
        md.update(head)
        md.update(tail)
        return md.digest().toHex()
    }

    private fun readAt(raf: RandomAccessFile, offset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        raf.seek(offset)
        raf.readFully(bytes)
        return bytes
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
