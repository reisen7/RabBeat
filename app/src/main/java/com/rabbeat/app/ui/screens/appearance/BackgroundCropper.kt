package com.rabbeat.app.ui.screens.appearance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rabbeat.app.R
import com.rabbeat.app.ui.theme.LocalAccentPalette
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackgroundCropper(
    source: File,
    onDone: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val accent = LocalAccentPalette.current
    val windowSize = LocalWindowInfo.current.containerSize

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(source) {
                val maxEdge = (maxOf(windowSize.width, windowSize.height) * 2.5f).roundToInt()
                val decoded = decode(source, maxEdge)
                if (decoded == null) {
                    Toast.makeText(context, R.string.crop_open_failed, Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    bitmap = decoded
                }
            }
            bitmap?.let { bmp ->
                CropEditor(bmp, windowSize, onDone, onDismiss)
            } ?: CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(28.dp),
                color = accent.color,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun CropEditor(
    bitmap: Bitmap,
    windowSize: IntSize,
    onDone: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()
    var scale by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var touching by remember { mutableStateOf(false) }
    var minS by remember { mutableFloatStateOf(0f) }
    var frameW by remember { mutableFloatStateOf(0f) }
    var frameH by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }

    fun animateTransform(targetScale: Float, targetOffset: Offset) {
        scope.launch {
            coroutineScope {
                launch { Animatable(scale).animateTo(targetScale, tween(250)) { scale = value } }
                launch {
                    Animatable<Offset, AnimationVector2D>(offset, Offset.VectorConverter)
                        .animateTo(targetOffset, tween(250)) { offset = value }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                enabled = !saving,
                onClick = {
                    if (scale <= 0f || saving) return@TextButton
                    saving = true
                    scope.launch {
                        val out = withContext(Dispatchers.Default) {
                            render(bitmap, windowSize, frameW, frameH, scale, offset)
                        }
                        if (out != null) {
                            bitmap.recycle()
                            onDone(out)
                        } else {
                            saving = false
                        }
                    }
                },
            ) {
                Text(
                    stringResource(R.string.action_done),
                    color = accent.color,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val margin = with(density) { 24.dp.toPx() }
            val availW = (constraints.maxWidth - margin * 2).coerceAtLeast(1f)
            val availH = (constraints.maxHeight - margin * 2).coerceAtLeast(1f)
            val aspect = windowSize.width.toFloat() / windowSize.height.toFloat()
            var fitW = availH * aspect
            var fitH = availH
            if (fitW > availW) {
                fitW = availW
                fitH = fitW / aspect
            }
            val frameLeft = (constraints.maxWidth - fitW) / 2f
            val frameTop = (constraints.maxHeight - fitH) / 2f
            val localMin = minScale(imgW, imgH, fitW, fitH)
            SideEffect {
                frameW = fitW
                frameH = fitH
                minS = localMin
            }
            LaunchedEffect(localMin) { if (scale == 0f) scale = localMin }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(localMin) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (scale <= 0f || localMin <= 0f) return@detectTransformGestures
                            val next = (scale * zoom).coerceIn(localMin, localMin * 5)
                            scale = next
                            offset = clampOffset(offset + pan, imgW, imgH, fitW, fitH, next)
                        }
                    }
                    .pointerInput(localMin) {
                        detectTapGestures(
                            onDoubleTap = { pos ->
                                if (scale <= 0f || localMin <= 0f) return@detectTapGestures
                                val target = if (scale > localMin * 1.05f) localMin else localMin * 2f
                                val center = Offset(
                                    frameLeft + fitW / 2f,
                                    frameTop + fitH / 2f,
                                )
                                val tap = pos - center
                                val next = clampOffset(
                                    tap - (tap - offset) * (target / scale),
                                    imgW, imgH, fitW, fitH, target,
                                )
                                animateTransform(target, next)
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            touching = true
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                            touching = false
                        }
                    },
            ) {
                if (scale > 0f) {
                    val dispW = imgW * scale
                    val dispH = imgH * scale
                    val cx = size.width / 2f + offset.x
                    val cy = size.height / 2f + offset.y
                    clipRect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH) {
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(
                                (cx - dispW / 2f).roundToInt(),
                                (cy - dispH / 2f).roundToInt(),
                            ),
                            dstSize = IntSize(
                                dispW.roundToInt().coerceAtLeast(1),
                                dispH.roundToInt().coerceAtLeast(1),
                            ),
                            filterQuality = FilterQuality.High,
                        )
                    }
                }
                val overlay = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                    addRect(Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH))
                    fillType = PathFillType.EvenOdd
                }
                drawPath(overlay, Color.Black.copy(alpha = 0.6f))
                drawRect(
                    Color.White,
                    topLeft = Offset(frameLeft, frameTop),
                    size = Size(frameW, frameH),
                    style = Stroke(with(density) { 1.dp.toPx() }),
                )
                if (touching) {
                    val line = Color.White.copy(alpha = 0.35f)
                    for (i in 1..2) {
                        val x = frameLeft + frameW * i / 3f
                        drawLine(line, Offset(x, frameTop), Offset(x, frameTop + frameH))
                        val y = frameTop + frameH * i / 3f
                        drawLine(line, Offset(frameLeft, y), Offset(frameLeft + frameW, y))
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            TextButton(onClick = {
                if (minS > 0f) animateTransform(minS, Offset.Zero)
            }) {
                Text(stringResource(R.string.crop_reset), color = Color.White)
            }
        }
    }
}

private fun render(
    bitmap: Bitmap,
    windowSize: IntSize,
    frameW: Float,
    frameH: Float,
    scale: Float,
    offset: Offset,
): Bitmap? {
    if (scale <= 0f || frameW <= 0f || frameH <= 0f) return null
    val rect = cropRect(bitmap.width.toFloat(), bitmap.height.toFloat(), frameW, frameH, scale, offset)
    val down = minOf(1f, 2400f / maxOf(windowSize.width, windowSize.height))
    val outW = (windowSize.width * down).roundToInt().coerceAtLeast(1)
    val outH = (windowSize.height * down).roundToInt().coerceAtLeast(1)
    return runCatching {
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val src = android.graphics.Rect(
            rect.left.roundToInt().coerceIn(0, bitmap.width),
            rect.top.roundToInt().coerceIn(0, bitmap.height),
            rect.right.roundToInt().coerceIn(0, bitmap.width),
            rect.bottom.roundToInt().coerceIn(0, bitmap.height),
        )
        Canvas(out).drawBitmap(
            bitmap,
            src,
            android.graphics.Rect(0, 0, outW, outH),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        out
    }.getOrNull()
}

private suspend fun decode(file: File, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val imageSource = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                val size = info.size
                val factor = maxOf(size.width, size.height).toFloat() / maxEdge
                if (factor > 1f) {
                    decoder.setTargetSize(
                        (size.width / factor).roundToInt().coerceAtLeast(1),
                        (size.height / factor).roundToInt().coerceAtLeast(1),
                    )
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@withContext null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val raw = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@withContext null
            raw.applyExif(file)
        }
    }.getOrNull()
}

private fun Bitmap.applyExif(file: File): Bitmap {
    val orientation = runCatching {
        file.inputStream().use { ExifInterface(it) }
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }
    return runCatching {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
            .also { if (it != this) recycle() }
    }.getOrDefault(this)
}
