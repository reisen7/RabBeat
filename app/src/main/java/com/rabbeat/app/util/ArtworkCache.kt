package com.rabbeat.app.util

import android.content.Context
import android.net.Uri
import java.io.File

/** Writes embedded cover art into the app cache so Coil can load it as a file URI. */
object ArtworkCache {
    fun write(context: Context, id: String, picture: ByteArray?): String? {
        if (picture == null || picture.size < 32) return null
        val file = File(context.cacheDir, "covers/$id.jpg")
        return runCatching {
            file.parentFile?.mkdirs()
            file.writeBytes(picture)
            Uri.fromFile(file).toString()
        }.getOrNull()
    }
}
