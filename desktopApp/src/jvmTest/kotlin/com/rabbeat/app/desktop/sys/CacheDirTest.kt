package com.rabbeat.app.desktop.sys

import com.rabbeat.app.desktop.library.LibraryScanner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheDirTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("rabbeat-cache").toFile()
        System.setProperty("rabbeat.dataDir", dir.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty("rabbeat.dataDir")
        dir.deleteRecursively()
    }

    @Test
    fun cacheRootDefaultsUnderDataDir() {
        val s = DesktopSettings()
        assertEquals(File(LibraryScanner.dataDir(), "cache").absolutePath, s.cacheRoot().absolutePath)
    }

    @Test
    fun customCacheDirResolvesAndPersists() {
        val s = DesktopSettings()
        val custom = File(dir, "elsewhere/RabBeatCache")
        s.cacheDir = custom.absolutePath
        assertEquals(custom.absolutePath, s.cacheRoot().absolutePath)
        assertEquals(custom.absolutePath, DesktopSettings().cacheDir)
    }
}
