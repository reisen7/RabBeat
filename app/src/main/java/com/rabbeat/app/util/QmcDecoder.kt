package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android entry point for QMC decryption: opens the source [Uri] as a
 * [FileChannel] and delegates to the platform-agnostic [QmcDecoderCore].
 */
@Singleton
class QmcDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val core = QmcDecoderCore(File(context.noBackupFilesDir, "qmc"))

    /**
     * Decrypts [source] into app storage, reusing a previous result when
     * [cacheKey] matches. Returns null for non-QMC, musicex (external-key) or
     * corrupt files.
     */
    fun decode(source: Uri, cacheKey: String): QmcDecoderCore.Decoded? {
        core.cached(cacheKey)?.let { return it }
        return openChannel(source)?.let { (channel, descriptor) ->
            try {
                core.decode(channel, cacheKey)
            } finally {
                runCatching { channel.close() }
                runCatching { descriptor?.close() }
            }
        }
    }

    private fun openChannel(source: Uri): Pair<FileChannel, android.os.ParcelFileDescriptor?>? {
        val path = source.path
        if (source.scheme == "file" || source.scheme == null) {
            val file = File(path ?: return null)
            return if (file.isFile) file.inputStream().channel to null else null
        }
        val descriptor = context.contentResolver.openFileDescriptor(source, "r") ?: return null
        return java.io.FileInputStream(descriptor.fileDescriptor).channel to descriptor
    }

    companion object {
        fun isQmc(name: String): Boolean = QmcDecoderCore.isQmc(name)
    }
}
