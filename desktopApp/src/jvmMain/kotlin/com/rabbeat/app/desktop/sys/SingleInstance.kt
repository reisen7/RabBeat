package com.rabbeat.app.desktop.sys
import com.rabbeat.app.desktop.library.LibraryScanner

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import kotlin.concurrent.thread

object SingleInstance {
    @Volatile var onActivate: (() -> Unit)? = null

    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    @Synchronized
    fun acquire(): Boolean {
        val dir = LibraryScanner.dataDir().apply { mkdirs() }
        val ch = runCatching { RandomAccessFile(File(dir, "instance.lock"), "rw").channel }
            .getOrNull() ?: return true
        val fl = try {
            ch.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (fl == null) {
            runCatching { ch.close() }
            notifyRunning(dir)
            return false
        }
        channel = ch
        lock = fl
        runCatching {
            val s = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            server = s
            File(dir, "instance.port").writeText(s.localPort.toString())
            thread(isDaemon = true, name = "rabbeat-single-instance") {
                while (!s.isClosed) {
                    try {
                        s.accept().close()
                        onActivate?.invoke()
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }
        return true
    }

    private fun notifyRunning(dir: File) {
        val port = runCatching { File(dir, "instance.port").readText().trim().toInt() }
            .getOrNull() ?: return
        runCatching {
            Socket().use { it.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1500) }
        }
    }

    @Synchronized
    internal fun release() {
        runCatching { server?.close() }
        runCatching { lock?.release() }
        runCatching { channel?.close() }
        server = null
        lock = null
        channel = null
        onActivate = null
    }
}
