package com.rabbeat.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.bitmapConfig
import coil3.size.Scale
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.util.appLogger
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/** True while a custom background image is behind the content. */
val LocalHasBackgroundImage = staticCompositionLocalOf { false }

/** How opaque cards are while a background image is visible (0 = clear, 1 = solid). */
val LocalCardOpacity = compositionLocalOf { 0.3f }

@Composable
fun RabBeatBackground(
    appearance: AppearanceSettings,
    modifier: Modifier = Modifier,
    onBackdropReady: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val uri = appearance.backgroundUri
    val hasImage = appearance.backgroundEnabled && !uri.isNullOrBlank()

    CompositionLocalProvider(
        LocalHasBackgroundImage provides hasImage,
        LocalCardOpacity provides appearance.cardOpacity,
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(palette.background),
        ) {
            if (hasImage) {
                BlurredBackdrop(uri = uri!!, blurDp = appearance.backgroundBlur, onReady = onBackdropReady)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.background.copy(alpha = appearance.backgroundDim * 0.72f),
                                    palette.background.copy(alpha = appearance.backgroundDim),
                                    palette.background.copy(alpha = (appearance.backgroundDim + 0.18f).coerceAtMost(0.96f)),
                                ),
                            ),
                        ),
                )
            }
            content()
        }
    }
}

/**
 * Renders the background as a static bitmap. The decode and blur happen once on
 * Dispatchers.IO — a live Modifier.blur RenderEffect re-rasterizes the full
 * screen every frame, which is what made scrolling stutter.
 */
@OptIn(FlowPreview::class)
@Composable
private fun BlurredBackdrop(uri: String, blurDp: Float, onReady: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val container = LocalWindowInfo.current.containerSize
    val blur by rememberUpdatedState(blurDp)
    val ready by rememberUpdatedState(onReady)
    val logger = remember { context.appLogger() }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, container) {
        var notified = false
        var first = true
        snapshotFlow { blur }
            .distinctUntilChanged()
            .debounce {
                if (first) {
                    first = false
                    0L
                } else {
                    160L
                }
            }
            .collectLatest { dp ->
                value = withContext(Dispatchers.IO) {
                    val t0 = android.os.SystemClock.elapsedRealtime()
                    runCatching { loadBackdrop(context, uri, dp * density, container) }
                        .onSuccess {
                            logger.d("Background", "backdrop decoded in ${android.os.SystemClock.elapsedRealtime() - t0}ms")
                        }
                        .onFailure { e -> logger.w("Background", "backdrop decode failed", e) }
                        .getOrNull()
                }
                if (!notified) {
                    notified = true
                    ready()
                }
            }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private suspend fun loadBackdrop(context: Context, uri: String, blurPx: Float, container: IntSize): ImageBitmap? {
    val shrink = if (blurPx > 2f) 4 else 1
    val w = (container.width / shrink).coerceAtLeast(64)
    val h = (container.height / shrink).coerceAtLeast(64)
    val result = SingletonImageLoader.get(context).execute(
        ImageRequest.Builder(context)
            .data(uri)
            .size(w, h)
            .scale(Scale.FILL)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .build(),
    )
    val decoded = (result.image as? coil3.BitmapImage)?.bitmap ?: return null
    if (blurPx <= 2f) return decoded.asImageBitmap()
    val radius = (blurPx / shrink).roundToInt().coerceIn(1, 96)
    return boxBlur(decoded, radius).asImageBitmap()
}

/** Gaussian approximation via three box-blur passes; edges replicate like Rectangle treatment. */
private fun boxBlur(bitmap: Bitmap, radius: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (radius < 1 || w < 2 || h < 2) return bitmap
    val src = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
    val dst = IntArray(w * h)
    repeat(3) {
        boxBlurHorizontal(src, dst, w, h, radius)
        boxBlurVertical(dst, src, w, h, radius)
    }
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
        it.setPixels(src, 0, w, 0, 0, w, h)
    }
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val window = 2 * radius + 1
    for (y in 0 until h) {
        val row = y * w
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (x in -radius..radius) {
            val c = src[row + x.coerceIn(0, w - 1)]
            a += c ushr 24
            r += c shr 16 and 0xFF
            g += c shr 8 and 0xFF
            b += c and 0xFF
        }
        for (x in 0 until w) {
            dst[row + x] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
            val add = src[row + (x + radius + 1).coerceAtMost(w - 1)]
            val remove = src[row + (x - radius).coerceAtLeast(0)]
            a += (add ushr 24) - (remove ushr 24)
            r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
            g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val window = 2 * radius + 1
    for (x in 0 until w) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (y in -radius..radius) {
            val c = src[y.coerceIn(0, h - 1) * w + x]
            a += c ushr 24
            r += c shr 16 and 0xFF
            g += c shr 8 and 0xFF
            b += c and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
            val add = src[(y + radius + 1).coerceAtMost(h - 1) * w + x]
            val remove = src[(y - radius).coerceAtLeast(0) * w + x]
            a += (add ushr 24) - (remove ushr 24)
            r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
            g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
            b += (add and 0xFF) - (remove and 0xFF)
        }
    }
}
