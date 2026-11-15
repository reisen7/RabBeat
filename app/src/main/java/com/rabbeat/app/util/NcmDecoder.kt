package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android entry point for `.ncm` decryption: resolves the source [Uri] into a
 * stream and delegates to the platform-agnostic [NcmDecoderCore].
 */
@Singleton
class NcmDecoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val core = NcmDecoderCore(File(context.noBackupFilesDir, "ncm"))

    /**
     * Decrypts [source] into app storage. [cacheKey] should identify the source file
     * (uri id, size and modified time) so a rescan reuses the previous result.
     * Returns null when the stream is missing, unreadable or not an NCM file.
     */
    fun decode(source: Uri, cacheKey: String): NcmDecoderCore.Decoded? = runCatching {
        context.contentResolver.openInputStream(source)?.use { core.decode(it, cacheKey) }
    }.getOrNull()
}
