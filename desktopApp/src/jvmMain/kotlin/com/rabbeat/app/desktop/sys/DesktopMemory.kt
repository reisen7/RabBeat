package com.rabbeat.app.desktop.sys

import coil3.memory.MemoryCache
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.delay

/**
 * Drops UI caches and asks Windows to shrink the working set while the window
 * is minimized or in the tray. Playback stays up. Opening the window again
 * reloads covers and the background.
 */
internal object DesktopMemory {
    var coverCache: MemoryCache? = null

    suspend fun park() {
        runCatching { coverCache?.clear() }
        System.gc()
        delay(400)
        WinNative.trimWorkingSet()
        delay(1200)
        WinNative.trimWorkingSet()
    }
}
