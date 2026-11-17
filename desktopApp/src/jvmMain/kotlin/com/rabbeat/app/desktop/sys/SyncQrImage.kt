package com.rabbeat.app.desktop.sys

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

/** Quiet-zone QR on white, so a phone can read it against the dark settings card. */
fun syncQrBitmap(text: String, size: Int = 512): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) {
        for (x in 0 until size) {
            image.setRGB(x, y, if (matrix.get(x, y)) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return image.toComposeImageBitmap()
}
