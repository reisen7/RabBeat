package com.rabbeat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ArtworkColorCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, Color>()

    suspend fun dominant(uri: String?): Color? {
        if (uri.isNullOrBlank()) return null
        cache[uri]?.let { return it }
        val color = withContext(Dispatchers.IO) {
            val bitmap = loadBitmap(uri) ?: return@withContext null
            val palette = Palette.from(bitmap).maximumColorCount(12).generate()
            val rgb = palette.vibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
            rgb?.let(::Color)
        }
        if (color != null) cache[uri] = color
        return color
    }

    private suspend fun loadBitmap(uri: String): Bitmap? {
        val name = uri.substringAfterLast('/')
        val id = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (id != 0) {
            return BitmapFactory.decodeResource(context.resources, id)?.let { decoded ->
                if (decoded.config == Bitmap.Config.HARDWARE) {
                    decoded.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    decoded
                }
            }
        }
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(64)
            .allowHardware(false)
            .build()
        return runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
    }
}
