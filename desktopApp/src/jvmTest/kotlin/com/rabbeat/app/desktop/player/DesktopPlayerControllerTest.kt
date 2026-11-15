package com.rabbeat.app.desktop.player

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.desktop.library.LibraryScanner
import com.rabbeat.app.desktop.sys.DesktopSettings
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackOrigin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopPlayerControllerTest {

    private lateinit var dir: File
    private lateinit var db: RabBeatDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var player: DesktopPlayerController

    @BeforeTest
    fun setUp() {
        Assume.assumeTrue("no usable audio mixer on this machine", mixerAvailable())
        dir = kotlin.io.path.createTempDirectory("rabbeat-test").toFile()
        System.setProperty("rabbeat.dataDir", File(dir, "data").absolutePath)
        db = Room.databaseBuilder<RabBeatDatabase>(File(dir, "test.db").absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .fallbackToDestructiveMigration(true)
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        player = DesktopPlayerController(db, scope)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        runBlocking { delay(100) }
        runCatching { db.close() }
        System.clearProperty("rabbeat.dataDir")
        dir.deleteRecursively()
    }

    @Test
    fun positionAdvancesWithinOneSecond() = runBlocking {
        val f = writeWav(File(dir, "a.wav"), 6.0)
        player.playQueue(listOf(track("t1", f, 6000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying }, "playback never started")
        delay(100)
        val p1 = player.state.value.positionMs
        delay(700)
        val p2 = player.state.value.positionMs
        assertTrue(p2 > p1, "position did not advance: $p1 -> $p2")
        assertTrue(p2 < 4000, "position ran away: $p2")
    }

    @Test
    fun pauseHoldsPositionAndResumeContinues() = runBlocking {
        val f = writeWav(File(dir, "b.wav"), 6.0)
        player.playQueue(listOf(track("t1", f, 6000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        assertTrue(await(2000) { player.state.value.positionMs > 300 }, "never reached 300ms")
        player.toggle()
        assertTrue(await(2000) { !player.state.value.isPlaying }, "did not pause")
        val paused = player.state.value.positionMs
        delay(500)
        val still = player.state.value.positionMs
        assertTrue(abs(still - paused) < 150, "position moved while paused: $paused -> $still")
        player.toggle()
        assertTrue(
            await(3000) { player.state.value.positionMs > paused + 100 },
            "resume did not continue from $paused (now ${player.state.value.positionMs})",
        )
    }

    @Test
    fun seekToJumpsToTargetPosition() = runBlocking {
        val f = writeWav(File(dir, "c.wav"), 8.0)
        player.playQueue(listOf(track("t1", f, 8000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        player.seekTo(3000)
        assertTrue(
            await(4000) { player.state.value.positionMs in 2600..3600 },
            "position after seekTo(3000): ${player.state.value.positionMs}",
        )
    }

    @Test
    fun corruptFileAutoAdvancesToNextTrack() = runBlocking {
        val good1 = writeWav(File(dir, "d1.wav"), 6.0)
        val corrupt = File(dir, "corrupt.mp3").apply { writeBytes(ByteArray(4096) { ((it * 31) % 256).toByte() }) }
        val good2 = writeWav(File(dir, "d2.wav"), 6.0)
        player.playQueue(
            listOf(track("g1", good1, 6000), track("bad", corrupt, 6000), track("g2", good2, 6000)),
            0,
        )
        assertTrue(await(4000) { player.state.value.isPlaying })
        player.next()
        assertTrue(
            await(6000) { player.state.value.currentTrack?.id == "g2" && player.state.value.isPlaying },
            "did not auto-advance past corrupt file; current=${player.state.value.currentTrack?.id} " +
                "playing=${player.state.value.isPlaying} error=${player.state.value.error}",
        )
    }

    @Test
    fun shufflePreviousReturnsToActuallyPlayedTrack() = runBlocking {
        val files = (1..4).map { writeWav(File(dir, "s$it.wav"), 6.0) }
        val tracks = files.mapIndexed { i, f -> track("s${i + 1}", f, 6000) }
        player.playQueue(tracks, 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        player.toggleShuffle()
        player.next()
        assertTrue(await(4000) { player.state.value.isPlaying && player.state.value.currentTrack?.id != "s1" })
        val second = player.state.value.currentTrack!!.id
        player.next()
        assertTrue(await(4000) { player.state.value.currentTrack?.id !in setOf("s1", second) })
        player.previous()
        assertTrue(
            await(4000) { player.state.value.currentTrack?.id == second },
            "shuffle previous did not return to $second; current=${player.state.value.currentTrack?.id}",
        )
    }

    @Test
    fun volumeAndMuteDriveMasterGain() = runBlocking {
        val f = writeWav(File(dir, "v.wav"), 6.0)
        player.playQueue(listOf(track("t1", f, 6000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        val ctrl = player.gainControl()
        Assume.assumeTrue("no MASTER_GAIN control on this mixer", ctrl != null)
        player.setVolume(0.5f)
        delay(100)
        assertEquals(20f * log10(0.5f), player.gainControl()!!.value, 0.5f)
        player.toggleMute()
        delay(100)
        assertEquals(
            ctrl!!.minimum,
            player.gainControl()!!.value,
            0.5f,
            "mute should clamp gain to control minimum",
        )
        player.toggleMute()
        assertTrue(!player.muted.value)
    }

    @Test
    fun loadingClearsAfterPlaybackStarts() = runBlocking {
        val f = writeWav(File(dir, "l.wav"), 6.0)
        player.playQueue(listOf(track("t1", f, 6000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        assertTrue(!player.loading.value, "loading still true after playback started")
        assertEquals(1f, player.volume.value)
    }

    @Test
    fun dataDirPrefersSystemProperty() {
        assertEquals(File(dir, "data").absolutePath, LibraryScanner.dataDir().absolutePath)
    }

    @Test
    fun volumeAndMutePersistToSettings() = runBlocking {
        val f = writeWav(File(dir, "p.wav"), 6.0)
        player.playQueue(listOf(track("t1", f, 6000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        player.setVolume(0.4f)
        player.toggleMute()
        val settingsFile = File(LibraryScanner.dataDir(), "settings.properties")
        assertTrue(settingsFile.isFile, "settings.properties not written")
        val reloaded = DesktopSettings(settingsFile)
        assertEquals(0.4f, reloaded.volume, 0.001f)
        assertTrue(reloaded.muted)
    }

    @Test
    fun toggleAfterNaturalEndReplaysFromStart() = runBlocking {
        val f = writeWav(File(dir, "e.wav"), 5.0)
        player.playQueue(listOf(track("t1", f, 5000)), 0)
        assertTrue(await(4000) { player.state.value.isPlaying })
        assertTrue(await(8000) { !player.state.value.isPlaying }, "track never ended")
        player.toggle()
        assertTrue(await(3000) { player.state.value.isPlaying }, "toggle did not replay")
        delay(400)
        assertTrue(
            player.state.value.positionMs < 2500,
            "replay started near the end: ${player.state.value.positionMs}",
        )
    }

    private suspend fun await(timeoutMs: Long, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            delay(50)
        }
        return false
    }

    private fun track(id: String, file: File, durationMs: Long) = Track(
        id = id, sourceId = "test", remoteId = null, title = "Track $id", artist = "t",
        artistId = null, album = "a", albumId = null, durationMs = durationMs,
        trackNumber = null, discNumber = null, year = null, mimeType = "audio/wav",
        bitrate = null, sampleRate = 44100, fileSize = null,
        uri = file.toURI().toString(), artworkUri = null,
        origin = TrackOrigin.LOCAL, downloadState = DownloadState.DOWNLOADED,
        isFavorite = false, playCount = 0, lastPlayedAt = null, addedAt = 0L,
        sourceType = SourceType.LOCAL, sourceName = "test", folder = "test",
    )

    private fun writeWav(file: File, seconds: Double, freq: Double = 440.0): File {
        val rate = 44100
        val frames = (rate * seconds).toInt()
        val data = ByteArray(frames * 4)
        var o = 0
        for (i in 0 until frames) {
            val v = (sin(2.0 * Math.PI * freq * i / rate) * 12000).toInt()
            data[o++] = (v and 0xff).toByte(); data[o++] = (v shr 8).toByte()
            data[o++] = (v and 0xff).toByte(); data[o++] = (v shr 8).toByte()
        }
        file.outputStream().use { out ->
            fun int32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
            fun int16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
            out.write("RIFF".toByteArray()); out.write(int32(36 + data.size)); out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray()); out.write(int32(16)); out.write(int16(1)); out.write(int16(2))
            out.write(int32(rate)); out.write(int32(rate * 4)); out.write(int16(4)); out.write(int16(16))
            out.write("data".toByteArray()); out.write(int32(data.size)); out.write(data)
        }
        return file
    }

    private fun mixerAvailable(): Boolean = runCatching {
        val format = AudioFormat(44100f, 16, 2, true, false)
        val l = AudioSystem.getSourceDataLine(format)
        l.open(format)
        l.close()
        true
    }.getOrDefault(false)
}
