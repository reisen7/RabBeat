package com.rabbeat.app.desktop.sys

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import javax.swing.SwingUtilities

/** Syncs the Windows 10/11 DWM title bar appearance with the active dark/light theme. */
object WindowsTitleBar {
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(
            hwnd: Pointer,
            dwAttribute: Int,
            pvAttribute: Pointer,
            cbAttribute: Int,
        ): Int

        companion object {
            val INSTANCE: Dwmapi? by lazy {
                runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
            }
        }
    }

    fun apply(window: Window, dark: Boolean) {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val applyNow = {
            runCatching {
                val dwm = Dwmapi.INSTANCE ?: return@runCatching
                val hwnd = Native.getWindowPointer(window)
                setInt(dwm, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
                // COLORREF is 0x00BBGGRR: #000000 for dark mode, #FFFFFF for light mode.
                setInt(dwm, hwnd, DWMWA_CAPTION_COLOR, if (dark) 0x00000000 else 0x00FFFFFF)
                setInt(dwm, hwnd, DWMWA_TEXT_COLOR, if (dark) 0x00F5F5F7 else 0x00111111)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) applyNow() else SwingUtilities.invokeLater { applyNow() }
    }

    private fun setInt(dwm: Dwmapi, hwnd: Pointer, attr: Int, value: Int) {
        dwm.DwmSetWindowAttribute(hwnd, attr, IntByReference(value).pointer, 4)
    }
}
