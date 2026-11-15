package com.rabbeat.app.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rabbeat.app.desktop.library.LibraryScanner
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

data class PixelCrop(val x: Int, val y: Int, val w: Int, val h: Int)

/** Largest aspect-correct rect that fits inside boxW x boxH. Returns (w, h). */
fun fitAspectRect(boxW: Float, boxH: Float, aspect: Float): Pair<Float, Float> {
    if (boxW <= 0f || boxH <= 0f || aspect <= 0f) return 0f to 0f
    var w = boxW
    var h = w / aspect
    if (h > boxH) {
        h = boxH
        w = h * aspect
    }
    return w to h
}

/** Clamp the crop-rect center so the whole rect stays inside the display box. */
fun clampCropCenter(
    cx: Float,
    cy: Float,
    rectW: Float,
    rectH: Float,
    boxW: Float,
    boxH: Float,
): Offset {
    val minX = rectW / 2f
    val maxX = (boxW - rectW / 2f).coerceAtLeast(minX)
    val minY = rectH / 2f
    val maxY = (boxH - rectH / 2f).coerceAtLeast(minY)
    return Offset(cx.coerceIn(minX, maxX), cy.coerceIn(minY, maxY))
}

/** Map a display-space crop rect (relative to displayed image origin) to source pixels, clamped. */
fun cropToSourcePixels(
    imgW: Int,
    imgH: Int,
    dispW: Float,
    dispH: Float,
    rectX: Float,
    rectY: Float,
    rectW: Float,
    rectH: Float,
): PixelCrop {
    if (imgW <= 0 || imgH <= 0 || dispW <= 0f || dispH <= 0f) return PixelCrop(0, 0, 1, 1)
    val px = (rectX * imgW / dispW).roundToInt().coerceIn(0, imgW - 1)
    val py = (rectY * imgH / dispH).roundToInt().coerceIn(0, imgH - 1)
    val pw = (rectW * imgW / dispW).roundToInt().coerceIn(1, imgW - px)
    val ph = (rectH * imgH / dispH).roundToInt().coerceIn(1, imgH - py)
    return PixelCrop(px, py, pw, ph)
}

/** Crop source pixels, downscale so the longer side is at most maxSide, write JPEG. */
fun cropImageToJpeg(
    source: File,
    crop: PixelCrop,
    maxSide: Int,
    outDir: File,
    outName: String,
): File? = runCatching {
    val img = org.jetbrains.skia.Image.makeFromEncoded(source.readBytes())
    val x = crop.x.coerceIn(0, img.width - 1)
    val y = crop.y.coerceIn(0, img.height - 1)
    val w = crop.w.coerceIn(1, img.width - x)
    val h = crop.h.coerceIn(1, img.height - y)
    val scale = minOf(1f, maxSide.toFloat() / maxOf(w, h))
    val outW = (w * scale).roundToInt().coerceAtLeast(1)
    val outH = (h * scale).roundToInt().coerceAtLeast(1)
    val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(outW, outH)
    surface.canvas.drawImageRect(
        img,
        Rect.makeXYWH(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()),
        Rect.makeXYWH(0f, 0f, outW.toFloat(), outH.toFloat()),
        SamplingMode.LINEAR,
        Paint(),
        true,
    )
    val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 92) ?: return null
    val out = File(outDir, outName)
    out.parentFile?.mkdirs()
    out.writeBytes(data.bytes)
    out
}.getOrNull()

@Composable
fun ImageCropDialog(
    source: File,
    aspect: Float,
    maxSide: Int,
    outName: String,
    title: String,
    onConfirm: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val scope = rememberCoroutineScope()
    val bitmap by produceState<ImageBitmap?>(null, source) {
        value = withContext(Dispatchers.IO) {
            runCatching { source.inputStream().use { loadImageBitmap(it) } }.getOrNull()
        }
    }
    var zoom by remember { mutableStateOf(0.7f) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            modifier = Modifier.defaultMinSize(minWidth = 640.dp, minHeight = 460.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))

                val img = bitmap
                if (img == null) {
                    Box(
                        Modifier.fillMaxWidth().height(360.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("无法读取图片", color = palette.secondaryLabel)
                    }
                } else {
                    val iw = img.width.toFloat()
                    val ih = img.height.toFloat()
                    val vw = viewport.width.toFloat()
                    val vh = viewport.height.toFloat()
                    val fit = if (iw > 0f && ih > 0f && vw > 0f && vh > 0f) minOf(vw / iw, vh / ih) else 0f
                    val dispW = iw * fit
                    val dispH = ih * fit
                    val imgX = (vw - dispW) / 2f
                    val imgY = (vh - dispH) / 2f
                    val (maxW, maxH) = fitAspectRect(dispW, dispH, aspect)
                    val rectW = maxW * zoom
                    val rectH = maxH * zoom
                    val c = clampCropCenter(
                        if (center == Offset.Unspecified) dispW / 2f else center.x,
                        if (center == Offset.Unspecified) dispH / 2f else center.y,
                        rectW, rectH, dispW, dispH,
                    )
                    val rectX = c.x - rectW / 2f
                    val rectY = c.y - rectH / 2f

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .onSizeChanged { viewport = it }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Scroll) {
                                        val delta = event.changes.first().scrollDelta.y
                                        zoom = (zoom - delta * 0.06f).coerceIn(0.2f, 1f)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                            .pointerInput(dispW, dispH, rectW, rectH) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    val cur = if (center == Offset.Unspecified) {
                                        Offset(dispW / 2f, dispH / 2f)
                                    } else {
                                        center
                                    }
                                    center = clampCropCenter(
                                        cur.x + drag.x, cur.y + drag.y,
                                        rectW, rectH, dispW, dispH,
                                    )
                                }
                            },
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (fit > 0f) {
                                drawImage(
                                    image = img,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(img.width, img.height),
                                    dstOffset = IntOffset(imgX.roundToInt(), imgY.roundToInt()),
                                    dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt()),
                                    filterQuality = FilterQuality.Medium,
                                )
                                val scrim = Color.Black.copy(alpha = 0.5f)
                                val l = imgX + rectX
                                val t = imgY + rectY
                                val r = l + rectW
                                val b = t + rectH
                                if (t > imgY) drawRect(scrim, Offset(imgX, imgY), Size(dispW, t - imgY))
                                if (b < imgY + dispH) {
                                    drawRect(scrim, Offset(imgX, b), Size(dispW, imgY + dispH - b))
                                }
                                if (l > imgX) drawRect(scrim, Offset(imgX, t), Size(l - imgX, b - t))
                                if (r < imgX + dispW) {
                                    drawRect(scrim, Offset(r, t), Size(imgX + dispW - r, b - t))
                                }
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(l, t),
                                    size = Size(rectW, rectH),
                                    style = Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("缩放", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
                    Spacer(Modifier.width(12.dp))
                    SeekBar(
                        fraction = ((zoom - 0.2f) / 0.8f).coerceIn(0f, 1f),
                        onSeek = { zoom = 0.2f + it * 0.8f },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${(zoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.secondaryLabel,
                        modifier = Modifier.width(40.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.hand()) { Text("取消") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        enabled = bitmap != null && viewport.width > 0 && viewport.height > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.color,
                            contentColor = accent.onColor,
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.hand(),
                        onClick = {
                            val bmp = bitmap ?: return@Button
                            val iw = bmp.width.toFloat()
                            val ih = bmp.height.toFloat()
                            val vw = viewport.width.toFloat()
                            val vh = viewport.height.toFloat()
                            val fit = minOf(vw / iw, vh / ih)
                            val dW = iw * fit
                            val dH = ih * fit
                            val (mW, mH) = fitAspectRect(dW, dH, aspect)
                            val rW = mW * zoom
                            val rH = mH * zoom
                            val cc = clampCropCenter(
                                if (center == Offset.Unspecified) dW / 2f else center.x,
                                if (center == Offset.Unspecified) dH / 2f else center.y,
                                rW, rH, dW, dH,
                            )
                            val spec = cropToSourcePixels(
                                bmp.width, bmp.height, dW, dH,
                                cc.x - rW / 2f, cc.y - rH / 2f, rW, rH,
                            )
                            scope.launch {
                                val out = withContext(Dispatchers.IO) {
                                    cropImageToJpeg(
                                        source, spec, maxSide,
                                        File(LibraryScanner.dataDir(), "art"), outName,
                                    )
                                }
                                if (out != null) onConfirm(out)
                            }
                        },
                    ) { Text("确定") }
                }
            }
        }
    }
}
