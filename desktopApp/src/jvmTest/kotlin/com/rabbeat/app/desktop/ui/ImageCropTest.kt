package com.rabbeat.app.desktop.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

class ImageCropTest {

    private fun makeSource(dir: File, w: Int, h: Int): File {
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawRect(
            Rect.makeWH(w.toFloat(), h.toFloat()),
            Paint().apply { color = 0xFF336699.toInt() },
        )
        val bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
        return File(dir, "src.png").apply { writeBytes(bytes) }
    }

    @Test
    fun fitAspectRectKeepsAspectInsideBox() {
        // Wider-than-aspect box: width limited.
        val (w1, h1) = fitAspectRect(800f, 600f, 16f / 9f)
        assertEquals(800f, w1, 0.01f)
        assertEquals(450f, h1, 0.01f)
        // Taller-than-aspect box: height limited.
        val (w2, h2) = fitAspectRect(400f, 600f, 16f / 9f)
        assertEquals(400f, w2, 0.01f)
        assertEquals(225f, h2, 0.01f)
        // Square aspect in a tall box.
        val (w3, h3) = fitAspectRect(400f, 600f, 1f)
        assertEquals(400f, w3, 0.01f)
        assertEquals(400f, h3, 0.01f)
        // Degenerate inputs.
        assertEquals(0f to 0f, fitAspectRect(0f, 600f, 1f))
        assertEquals(0f to 0f, fitAspectRect(400f, 600f, 0f))
    }

    @Test
    fun clampCenterKeepsRectInside() {
        val c1 = clampCropCenter(10f, 10f, 100f, 100f, 400f, 300f)
        assertEquals(50f, c1.x, 0.01f)
        assertEquals(50f, c1.y, 0.01f)
        val c2 = clampCropCenter(390f, 290f, 100f, 100f, 400f, 300f)
        assertEquals(350f, c2.x, 0.01f)
        assertEquals(250f, c2.y, 0.01f)
        val c3 = clampCropCenter(200f, 150f, 100f, 100f, 400f, 300f)
        assertEquals(200f, c3.x, 0.01f)
        assertEquals(150f, c3.y, 0.01f)
        // Rect as large as the box: center is pinned.
        val c4 = clampCropCenter(999f, 999f, 400f, 300f, 400f, 300f)
        assertEquals(200f, c4.x, 0.01f)
        assertEquals(150f, c4.y, 0.01f)
    }

    @Test
    fun cropToSourcePixelsMapsAndClamps() {
        // 1600x1200 image shown at 800x600 -> 2x scale.
        val p1 = cropToSourcePixels(1600, 1200, 800f, 600f, 100f, 50f, 400f, 300f)
        assertEquals(PixelCrop(200, 100, 800, 600), p1)
        // Zoom extremes.
        val full = cropToSourcePixels(1600, 1200, 800f, 600f, 0f, 0f, 800f, 600f)
        assertEquals(PixelCrop(0, 0, 1600, 1200), full)
        val tiny = cropToSourcePixels(1600, 1200, 800f, 600f, 400f, 300f, 0.4f, 0.4f)
        assertTrue(tiny.w >= 1 && tiny.h >= 1)
        // Rect pushed beyond the image edge is clamped inside.
        val over = cropToSourcePixels(1600, 1200, 800f, 600f, 780f, 590f, 100f, 100f)
        assertTrue(over.x >= 0 && over.y >= 0)
        assertTrue(over.x + over.w <= 1600)
        assertTrue(over.y + over.h <= 1200)
        // Degenerate display size.
        val deg = cropToSourcePixels(1600, 1200, 0f, 0f, 10f, 10f, 100f, 100f)
        assertEquals(PixelCrop(0, 0, 1, 1), deg)
    }

    @Test
    fun cropImageToJpegWritesScaledOutput() {
        val dir = java.nio.file.Files.createTempDirectory("crop-test").toFile()
        try {
            val src = makeSource(dir, 800, 400)
            // Crop a 400x200 region, maxSide 100 -> 100x50 output.
            val out = cropImageToJpeg(src, PixelCrop(100, 50, 400, 200), 100, dir, "out.jpg")!!
            assertTrue(out.isFile && out.length() > 0)
            val decoded = Image.makeFromEncoded(out.readBytes())
            assertEquals(100, decoded.width)
            assertEquals(50, decoded.height)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun cropImageToJpegNeverScalesUp() {
        val dir = java.nio.file.Files.createTempDirectory("crop-test-up").toFile()
        try {
            val src = makeSource(dir, 800, 400)
            // 50x50 crop with maxSide 2560 must stay 50x50.
            val out = cropImageToJpeg(src, PixelCrop(0, 0, 50, 50), 2560, dir, "small.jpg")!!
            val decoded = Image.makeFromEncoded(out.readBytes())
            assertEquals(50, decoded.width)
            assertEquals(50, decoded.height)
            // Crop clamped at the edge still produces a valid file.
            val edge = cropImageToJpeg(src, PixelCrop(790, 390, 100, 100), 512, dir, "edge.jpg")!!
            val d2 = Image.makeFromEncoded(edge.readBytes())
            assertEquals(10, d2.width)
            assertEquals(10, d2.height)
        } finally {
            dir.deleteRecursively()
        }
    }
}
