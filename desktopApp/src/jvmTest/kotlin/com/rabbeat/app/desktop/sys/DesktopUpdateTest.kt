package com.rabbeat.app.desktop.sys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUpdateTest {
    @Test
    fun newerReleaseBeatsCurrent() {
        assertTrue(isNewerRelease("v0.0.5", "0.0.4"))
        assertTrue(isNewerRelease("v0.0.10", "0.0.9"))
        assertFalse(isNewerRelease("v0.0.4", "0.0.4"))
        assertFalse(isNewerRelease("v0.0.4", "0.0.5"))
    }

    @Test
    fun prefersWindowsInstallerOverZipAndApk() {
        val picked = pickWindowsAsset(
            listOf(
                "https://github.com/reisen7/RabBeat/releases/download/v0.0.5/RabBeat-0.0.5.apk",
                "https://github.com/reisen7/RabBeat/releases/download/v0.0.5/RabBeat-0.0.5-windows-portable.zip",
                "https://github.com/reisen7/RabBeat/releases/download/v0.0.5/RabBeat-0.0.5-windows-setup.exe",
                "https://github.com/reisen7/RabBeat/releases/download/v0.0.5/RabBeat-0.0.5-windows-setup.exe.sha256",
            ),
        )
        assertEquals(
            "https://github.com/reisen7/RabBeat/releases/download/v0.0.5/RabBeat-0.0.5-windows-setup.exe",
            picked,
        )
    }

    @Test
    fun ignoresChecksumFiles() {
        assertNull(pickWindowsAsset(listOf("https://example.com/RabBeat-0.0.5.apk.sha256")))
    }
}
