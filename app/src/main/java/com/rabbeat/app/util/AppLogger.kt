package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.rabbeat.app.BuildConfig
import com.rabbeat.app.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * File logger for field debugging. Writes daily files to filesDir/logs,
 * prunes anything older than [KEEP_DAYS], and can bundle them into a zip
 * at a user-picked Uri for export.
 */
@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "logs")
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lines = Channel<String>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val writeLock = Mutex()
    @Volatile private var writer: BufferedWriter? = null
    @Volatile private var writerDay = ""
    private var writerBytes = 0L

    init {
        scope.launch(Dispatchers.IO) {
            prune()
            for (line in lines) append(line)
        }
        // Prune every 6h as well, so a long-running process still sheds old files.
        scope.launch(Dispatchers.IO) {
            while (true) { delay(6 * 3_600_000L); prune() }
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            e("Crash", "uncaught on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    fun d(tag: String, message: String) = log("D", tag, message, null)
    fun i(tag: String, message: String) = log("I", tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = log("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = log("E", tag, message, error)

    private fun log(level: String, tag: String, message: String, error: Throwable?) {
        when (level) {
            "D" -> Log.d(tag, message)
            "I" -> Log.i(tag, message)
            "W" -> Log.w(tag, message, error)
            else -> Log.e(tag, message, error)
        }
        val stack = error?.let {
            val out = StringWriter()
            it.printStackTrace(PrintWriter(out))
            "\n$out"
        }.orEmpty()
        lines.trySend("${timeFormat.format(Date())} $level/$tag: $message$stack")
    }

    private suspend fun append(line: String) = writeLock.withLock {
        val day = dayFormat.format(Date())
        if (writer == null || writerDay != day) {
            writer?.close()
            writer = null
            writerDay = day
            prune()
            openWriter()
        } else if (writerBytes + line.length > MAX_FILE_BYTES) {
            // Cap each day's file; continue into the next -N segment.
            writer?.close()
            writer = null
            openWriter()
        }
        writer?.run { write(line); newLine(); flush() }
        writerBytes += line.length + 1L
    }

    private fun openWriter() {
        dir.mkdirs()
        var segment = 0
        var file: File
        do {
            segment++
            val name = if (segment == 1) "rabbeat-$writerDay.log" else "rabbeat-$writerDay-$segment.log"
            file = File(dir, name)
        } while (file.exists() && file.length() >= MAX_FILE_BYTES)
        val fresh = !file.exists()
        writer = BufferedWriter(FileWriter(file, true))
        writerBytes = file.length()
        if (fresh) {
            writer?.write("# RabBeat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) | " +
                "${Build.MANUFACTURER} ${Build.MODEL} | API ${Build.VERSION.SDK_INT}\n")
        }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - KEEP_DAYS * 86_400_000L
        dir.listFiles { f -> f.extension == "log" && f.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /** Zips all retained log files into [target] (a SAF document Uri). */
    suspend fun exportTo(target: Uri): Boolean = writeLock.withLock {
        runCatching {
            context.contentResolver.openOutputStream(target)?.use { raw ->
                ZipOutputStream(raw.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry("device.txt"))
                    zip.write(
                        ("RabBeat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                            "${Build.MANUFACTURER} ${Build.MODEL}\n" +
                            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                            "exported ${timeFormat.format(Date())}\n").toByteArray(),
                    )
                    zip.closeEntry()
                    dir.listFiles { f -> f.extension == "log" }
                        ?.sortedBy { it.name }
                        ?.forEach { file ->
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                }
            } != null
        }.getOrDefault(false)
    }

    private companion object {
        const val KEEP_DAYS = 7L
        const val MAX_FILE_BYTES = 1_000_000L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppLoggerEntryPoint {
    fun appLogger(): AppLogger
}

/** Lets composables reach the logger without a ViewModel in between. */
fun Context.appLogger(): AppLogger =
    EntryPointAccessors.fromApplication(applicationContext, AppLoggerEntryPoint::class.java).appLogger()
