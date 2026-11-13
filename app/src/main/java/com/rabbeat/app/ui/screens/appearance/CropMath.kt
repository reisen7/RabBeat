package com.rabbeat.app.ui.screens.appearance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

/** Scale (view px per image px) at which the image exactly covers the frame. */
fun minScale(imageW: Float, imageH: Float, frameW: Float, frameH: Float): Float =
    maxOf(frameW / imageW, frameH / imageH)

/**
 * [offset] is the translation of the image center relative to the frame center in view px.
 * Clamps it so the frame stays fully covered by the image displayed at [scale].
 */
fun clampOffset(
    offset: Offset,
    imageW: Float,
    imageH: Float,
    frameW: Float,
    frameH: Float,
    scale: Float,
): Offset {
    val maxX = ((imageW * scale - frameW) / 2f).coerceAtLeast(0f)
    val maxY = ((imageH * scale - frameH) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

/** Output bitmap size for a frame aspect (width/height) with the long edge capped at [maxEdge]. */
fun outputSize(aspect: Float, maxEdge: Int): IntSize =
    if (aspect >= 1f) {
        IntSize(maxEdge, (maxEdge / aspect).toInt().coerceAtLeast(1))
    } else {
        IntSize((maxEdge * aspect).toInt().coerceAtLeast(1), maxEdge)
    }

/** Crop rectangle in image pixel coordinates for the given view transform. */
fun cropRect(
    imageW: Float,
    imageH: Float,
    frameW: Float,
    frameH: Float,
    scale: Float,
    offset: Offset,
): Rect {
    val left = ((imageW * scale - frameW) / 2f - offset.x) / scale
    val top = ((imageH * scale - frameH) / 2f - offset.y) / scale
    val width = frameW / scale
    val height = frameH / scale
    val l = left.coerceIn(0f, (imageW - width).coerceAtLeast(0f))
    val t = top.coerceIn(0f, (imageH - height).coerceAtLeast(0f))
    return Rect(l, t, l + width, t + height)
}
