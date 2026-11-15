package com.rabbeat.app.desktop

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rolling file logger — ~/.rabbeat/logs/yyyy-MM-dd.log, pruned past 7 days. */
object DesktopLog {
    private val dir = File(LibraryScanner.dataDir(), "logs").apply { mkdirs() }
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    init { prune() }

    fun i(tag: String, msg: String) = write("I", tag, msg)
    fun w(tag: String, msg: String) = write("W", tag, msg)
    fun e(tag: String, msg: String) = write("E", tag, msg)

    private fun write(level: String, tag: String, msg: String) {
        runCatching {
            File(dir, "${day.format(Date())}.log")
                .appendText("${time.format(Date())} $level/$tag $msg\n")
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    /** Merge all retained logs into one export file; returns it. */
    fun export(target: File): File {
        target.writeText(
            dir.listFiles()?.sortedBy { it.name }
                ?.joinToString("") { "=== ${it.name} ===\n" + it.readText() }
                .orEmpty(),
        )
        return target
    }

    fun dir(): File = dir
}
