package com.rabbeat.app.data.local

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the personal background photo inside app storage.
 *
 * The system photo picker only grants temporary read access to the gallery item, so the
 * picked image is copied here first (`source_pending.*`), then promoted to `source.*` once
 * the user confirms the crop. Cropped results are written as `background_*.jpg`.
 */
@Singleton
class BackgroundImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory: File
        get() = File(context.filesDir, "backgrounds").apply { mkdirs() }

    /** Copies [source] into app storage as the pending source file, or null on failure. */
    fun importSource(source: Uri): File? {
        val dir = directory
        dir.listFiles()?.forEach { file -> if (file.name.startsWith(PENDING_PREFIX)) file.delete() }
        val extension = when (context.contentResolver.getType(source)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val target = File(dir, "$PENDING_PREFIX$extension")
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            target.delete()
            return null
        }
        return target
    }

    /** Renames the pending source to the kept source file. No-op when nothing is pending. */
    fun promotePending(): File? {
        val pending = pendingFile() ?: return null
        val kept = File(directory, keptNameFor(pending))
        kept.delete()
        return if (pending.renameTo(kept)) kept else null
    }

    fun discardPending() {
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith(PENDING_PREFIX)) file.delete()
        }
    }

    /** The kept original image, used to re-adjust the crop later. */
    fun sourceFile(): File? =
        directory.listFiles()?.firstOrNull { it.name.startsWith(SOURCE_PREFIX) && !it.name.startsWith(PENDING_PREFIX) }

    /** Writes the cropped bitmap, removes older `background_*` files, returns a `file://` uri. */
    fun saveCropped(bitmap: Bitmap): String? {
        val dir = directory
        val target = File(dir, "background_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }.getOrDefault(false)
        if (!saved) {
            target.delete()
            return null
        }
        dir.listFiles()?.forEach { file ->
            if (file != target && file.name.startsWith("background_")) file.delete()
        }
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

    /** Removes everything in the backgrounds directory (remove background / reset). */
    fun clearAll() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private fun pendingFile(): File? =
        directory.listFiles()?.firstOrNull { it.name.startsWith(PENDING_PREFIX) }

    private fun keptNameFor(pending: File): String =
        SOURCE_PREFIX + pending.name.removePrefix(PENDING_PREFIX)

    companion object {
        private const val PENDING_PREFIX = "source_pending."
        private const val SOURCE_PREFIX = "source."
    }
}
