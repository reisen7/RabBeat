package com.rabbeat.app.ui.screens.appearance

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {

    @Test
    fun minScaleCoversWideImageByHeight() {
        // 4000x1000 image into 1000x2000 frame: height ratio wins.
        assertEquals(2f, minScale(4000f, 1000f, 1000f, 2000f), 1e-4f)
    }

    @Test
    fun centeredWideImageCropsFullHeight() {
        val rect = cropRect(4000f, 1000f, 1000f, 2000f, scale = 2f, offset = Offset.Zero)
        assertEquals(1750f, rect.left, 1e-3f)
        assertEquals(0f, rect.top, 1e-3f)
        assertEquals(2250f, rect.right, 1e-3f)
        assertEquals(1000f, rect.bottom, 1e-3f)
    }

    @Test
    fun zoomedInRectShrinksAroundCenter() {
        // scale 4 on the same image: crop is 250x500 centered.
        val rect = cropRect(4000f, 1000f, 1000f, 2000f, scale = 4f, offset = Offset.Zero)
        assertEquals(1875f, rect.left, 1e-3f)
        assertEquals(250f, rect.top, 1e-3f)
        assertEquals(2125f, rect.right, 1e-3f)
        assertEquals(750f, rect.bottom, 1e-3f)
    }

    @Test
    fun offsetBeyondEdgeIsClamped() {
        // At scale 2 the displayed image is 8000x2000 in a 1000x2000 frame:
        // x may move ±3500, y cannot move at all.
        val clamped = clampOffset(Offset(4000f, 50f), 4000f, 1000f, 1000f, 2000f, scale = 2f)
        assertEquals(Offset(3500f, 0f), clamped)
    }

    @Test
    fun tallImageCoversByWidth() {
        // 1000x4000 image into 1000x2000 frame: width ratio wins, y movable.
        assertEquals(1f, minScale(1000f, 4000f, 1000f, 2000f), 1e-4f)
        val clamped = clampOffset(Offset(10f, -2000f), 1000f, 4000f, 1000f, 2000f, scale = 1f)
        assertEquals(Offset(0f, -1000f), clamped)
        // Image center moved up by 1000 -> the frame shows the image's bottom half.
        val rect = cropRect(1000f, 4000f, 1000f, 2000f, scale = 1f, offset = clamped)
        assertEquals(0f, rect.left, 1e-3f)
        assertEquals(2000f, rect.top, 1e-3f)
        assertEquals(1000f, rect.right, 1e-3f)
        assertEquals(4000f, rect.bottom, 1e-3f)
    }

    @Test
    fun offsetShiftsCropRect() {
        val rect = cropRect(4000f, 1000f, 1000f, 2000f, scale = 2f, offset = Offset(700f, 0f))
        assertEquals(1400f, rect.left, 1e-3f)
        assertEquals(1900f, rect.right, 1e-3f)
    }
}
