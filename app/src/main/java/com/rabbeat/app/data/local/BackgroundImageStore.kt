package com.rabbeat.app.data.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the personal background photo inside app storage.
 *
 * The system photo picker only grants temporary read access to the gallery item, so the
 * picked image is copied here. That keeps the background working after a restart and even
 * if the user later deletes or moves the original photo.
 */
@Singleton
class BackgroundImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory: File
        get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    /** Copies [source] into app storage and returns a persistable `file://` uri, or null on failure. */
    fun import(source: Uri): String? {
        val dir = directory
        val extension = when (context.contentResolver.getType(source)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val target = File(dir, "background_${System.currentTimeMillis()}.$extension")
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            target.delete()
            return null
        }
        dir.listFiles()?.forEach { file -> if (file != target) file.delete() }
        return Uri.fromFile(target).toString()
    }

    /** Deletes a previously imported copy, or releases a persisted SAF grant from older versions. */
    fun release(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> uri.path?.let { File(it).delete() }
                "content" -> context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}
