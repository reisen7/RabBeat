package com.rabbeat.app.desktop.sys

import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.SystemTray
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pre-rasterized window icons. Windows asks for one exact pixel size
 * (16, 24, 32, …). Handing it the 512px master makes the taskbar and
 * tray scale that picture themselves, which is what looks soft.
 */
object AppIcons {
    private val sizes = intArrayOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    val images: List<BufferedImage> by lazy {
        sizes.map { size ->
            val stream = AppIcons::class.java.classLoader
                .getResourceAsStream("icons/rabbeat-$size.png")
                ?: error("缺少图标 icons/rabbeat-$size.png")
            stream.use { ImageIO.read(it) }
        }
    }

    /** Largest first. That is the order [java.awt.Window.setIconImages] expects. */
    fun windowIcons(): List<Image> = images.sortedByDescending { it.width }

    /**
     * Tray slot in physical pixels. [SystemTray.getTrayIconSize] is sometimes
     * already scaled, and sometimes stuck at 16; take whichever is larger so
     * a high-DPI tray is not given a 16px picture to stretch.
     */
    fun trayIcon(): BufferedImage {
        val slot = if (SystemTray.isSupported()) {
            SystemTray.getSystemTray().trayIconSize.width
        } else {
            16
        }
        val scale = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        val px = max(slot, (16 * scale).roundToInt())
        return images.minBy { abs(it.width - px) }
    }
}
