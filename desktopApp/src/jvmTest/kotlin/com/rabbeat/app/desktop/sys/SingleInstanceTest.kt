package com.rabbeat.app.desktop.sys

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingleInstanceTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = createTempDirectory("rabbeat-si").toFile()
        System.setProperty("rabbeat.dataDir", dir.absolutePath)
        SingleInstance.release()
    }

    @AfterTest
    fun tearDown() {
        SingleInstance.release()
        System.clearProperty("rabbeat.dataDir")
        dir.deleteRecursively()
    }

    @Test
    fun secondAcquireFailsAndActivatesFirst() {
        assertTrue(SingleInstance.acquire())
        val latch = CountDownLatch(1)
        SingleInstance.onActivate = { latch.countDown() }
        assertFalse(SingleInstance.acquire())
        assertTrue(latch.await(3, TimeUnit.SECONDS), "onActivate was not invoked")
    }
}
