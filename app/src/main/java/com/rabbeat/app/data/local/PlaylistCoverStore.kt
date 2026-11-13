package com.rabbeat.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom playlist cover files. Picked images are staged as `source_pending.*`,
 * cropped results are written as `cover_<sanitizedPlaylistId>_<ts>.jpg`
 * (the timestamp busts the image cache when a cover is replaced).
 */
@Singleton
class PlaylistCoverStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory: File
        get() = File(context.filesDir, "playlist_covers").apply { mkdirs() }

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

    fun discardPending() {
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith(PENDING_PREFIX)) file.delete()
        }
    }

    fun saveCropped(playlistId: String, bitmap: Bitmap): String? {
        val dir = directory
        val sanitized = sanitize(playlistId)
        val target = File(dir, "cover_${sanitized}_${System.currentTimeMillis()}.jpg")
        val saved = runCatching {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }.getOrDefault(false)
        if (!saved) {
            target.delete()
            return null
        }
        dir.listFiles()?.forEach { file ->
            if (file != target && file.name.startsWith("cover_${sanitized}_")) file.delete()
        }
        discardPending()
        return Uri.fromFile(target).toString()
    }

    fun release(playlistId: String) {
        val sanitized = sanitize(playlistId)
        directory.listFiles()?.forEach { file ->
            if (file.name.startsWith("cover_${sanitized}_")) file.delete()
        }
    }

    private fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val PENDING_PREFIX = "source_pending."
    }
}
