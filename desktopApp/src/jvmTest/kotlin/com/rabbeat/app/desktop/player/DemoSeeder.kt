package com.rabbeat.app.desktop.player

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.desktop.library.LibraryScanner
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.TrackOrigin
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.sin

/**
 * `gradlew :desktopApp:seedDemoLibrary` → builds a realistic demo library under
 * build/demo-data (or -Prabbeat.dataDir). Wipes and reseeds on every run.
 */
fun main(args: Array<String>) = runBlocking {
    val dir = args.getOrNull(0)?.let(::File)
        ?: System.getProperty("rabbeat.dataDir")?.let(::File)
        ?: error("pass the data dir as arg0 or -Drabbeat.dataDir")
    if (dir.exists()) dir.deleteRecursively()
    dir.mkdirs()
    System.setProperty("rabbeat.dataDir", dir.absolutePath)

    val db = Room.databaseBuilder<RabBeatDatabase>(File(dir, "rabbeat.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(true)
        .build()
    try {
        seed(db, dir)
        println("demo library seeded at ${dir.absolutePath}")
    } finally {
        db.close()
    }
}

private data class DemoAlbum(val artist: String, val album: String, val year: Int, val titles: List<String>)

private suspend fun seed(db: RabBeatDatabase, dir: File) {
    val albums = listOf(
        DemoAlbum("林夜航", "夜航电台", 2021, listOf("午夜信号", "霓虹港口", "慢速降落", "城市失眠症", "零点三十七", "雾中列车", "无线电告别", "夜航电台")),
        DemoAlbum("Nova Pulse", "Static Bloom", 2022, listOf("Aurora Rising", "Bloom", "Paper Moon", "Voltage", "Half-Light", "Signal Lost", "Bloom (Reprise)")),
        DemoAlbum("森沢かな", "玻璃鸟笼", 2020, listOf("ガラスの羽", "雨停时刻", "透明标本", "远雷", "玻璃鸟笼", "夜桜", "白い手紙")),
        DemoAlbum("Aurora Veil", "Midnight Circuit", 2023, listOf("Neon Rain", "Sunset Afterhours", "Midnight Circuit", "Low Tide", "Parallel", "Last Train Home")),
        DemoAlbum("陈汐", "潮汐之间", 2019, listOf("退潮", "深海漫步", "蓝色回声", "岸", "潮汐之间", "搁浅")),
        DemoAlbum("Nova Pulse", "Ghost Frequency", 2024, listOf("Spectral", "Warm Static", "Ghost Frequency", "Decoder", "Night Drive", "Echo Chamber")),
    )
    db.sourceDao().upsert(
        SourceEntity(
            id = LibraryScanner.LOCAL_SOURCE_ID,
            type = SourceType.LOCAL,
            displayName = "本地文件",
            configJson = "{}",
            status = SourceStatus.CONNECTED,
            lastSyncAt = null,
            createdAt = System.currentTimeMillis(),
        ),
    )

    val musicRoot = File(dir, "music")
    val artDir = File(dir, "art").apply { mkdirs() }
    val now = System.currentTimeMillis()
    val day = 86_400_000L
    val trackEntities = mutableListOf<TrackEntity>()
    val lyricTrack = "Aurora Rising" to "Nova Pulse"
    val lyricTrack2 = "玻璃鸟笼" to "森沢かな"
    var trackNo = 0

    albums.forEachIndexed { ai, demo ->
        val coverFile = File(artDir, "album_$ai.png")
        writeCoverPng(coverFile, demo.album, ai)
        val coverUri = coverFile.toURI().toString()
        val albumFolder = when {
            ai == 2 -> File(musicRoot, "Collection/${demo.artist}/${demo.album}")
            ai == 5 -> File(musicRoot, "Lossless/Nova Pulse/Ghost Frequency/Disc 1")
            else -> File(musicRoot, "Library/${demo.artist}/${demo.album}")
        }
        albumFolder.mkdirs()
        val albumFolderRel = albumFolder.relativeTo(musicRoot).path

        demo.titles.forEachIndexed { ti, title ->
            trackNo++
            val seconds = 8 + ((trackNo * 7) % 13) // 8..20s
            val file = File(albumFolder, "%02d - %s.wav".format(ti + 1, title))
            writeWav(file, seconds.toDouble(), 300.0 + (trackNo % 9) * 40 + ti * 15)
            val id = "dsk_" + sha1(file.absolutePath).take(24)
            val artistId = "dart_" + sha1(demo.artist).take(16)
            val albumId = "dalb_" + sha1("${demo.artist}|${demo.album}").take(16)
            val plays = (trackNo * 13 + ai * 5) % 41
            trackEntities += TrackEntity(
                id = id,
                sourceId = LibraryScanner.LOCAL_SOURCE_ID,
                remoteId = null,
                title = title,
                artist = demo.artist,
                artistId = artistId,
                album = demo.album,
                albumId = albumId,
                durationMs = seconds * 1000L,
                trackNumber = ti + 1,
                discNumber = if (ai == 5) 1 else null,
                year = demo.year,
                mimeType = "audio/wav",
                bitrate = 1411,
                sampleRate = 44100,
                fileSize = file.length(),
                uri = file.toURI().toString(),
                artworkUri = coverUri,
                origin = TrackOrigin.LOCAL,
                downloadState = DownloadState.NONE,
                isFavorite = false,
                playCount = plays,
                lastPlayedAt = if (plays > 0) now - (trackNo % 14) * day else null,
                addedAt = now - (40 - trackNo) * day,
                folder = albumFolderRel,
            )
            if (title == lyricTrack.first || title == lyricTrack2.first) {
                writeLrc(File(albumFolder, file.nameWithoutExtension + ".lrc"), title)
            }
        }
    }

    db.trackDao().upsertAll(trackEntities)
    db.albumDao().upsertAll(
        albums.mapIndexed { ai, demo ->
            AlbumEntity(
                id = "dalb_" + sha1("${demo.artist}|${demo.album}").take(16),
                sourceId = LibraryScanner.LOCAL_SOURCE_ID,
                title = demo.album,
                artist = demo.artist,
                year = demo.year,
                artworkUri = File(artDir, "album_$ai.png").toURI().toString(),
                trackCount = demo.titles.size,
                addedAt = now - 40 * day,
            )
        },
    )
    db.artistDao().upsertAll(
        albums.groupBy { it.artist }.map { (artist, own) ->
            ArtistEntity(
                id = "dart_" + sha1(artist).take(16),
                sourceId = LibraryScanner.LOCAL_SOURCE_ID,
                name = artist,
                artworkUri = own.indices.first().let { File(artDir, "album_${albums.indexOfFirst { a -> a.artist == artist }}.png").toURI().toString() },
                albumCount = own.size,
                trackCount = own.sumOf { it.titles.size },
            )
        },
    )

    val liked = LikedPlaylist(db.playlistDao(), db.trackDao())
    liked.ensure()
    trackEntities.filterIndexed { i, _ -> i % 8 == 0 }.forEach { liked.toggle(it.id) }

    val covers = File(dir, "covers").apply { mkdirs() }
    val plCover = File(covers, "pl_night.png")
    writeCoverPng(plCover, "夜", 3)
    val playlists = listOf(
        Triple("demo_pl_drive", "深夜循环", plCover.toURI().toString()),
        Triple("demo_pl_commute", "通勤歌单", null),
        Triple("demo_pl_rain", "白噪音·雨", null),
    )
    playlists.forEachIndexed { pi, (pid, name, cover) ->
        db.playlistDao().upsert(
            PlaylistEntity(
                id = pid, name = name, isSystem = false,
                createdAt = now - (30 - pi) * day, updatedAt = now - pi * day,
                sortOrder = pi, coverUri = cover,
            ),
        )
        val members = trackEntities.filterIndexed { i, _ -> (i + pi * 3) % 5 == 0 }.take(9)
        db.playlistDao().upsertTracks(
            members.mapIndexed { i, t -> PlaylistTrackCrossRef(pid, t.id, i, now - pi * day) },
        )
    }
}

private fun sha1(text: String): String =
    MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private fun writeWav(file: File, seconds: Double, freq: Double) {
    val rate = 44100
    val frames = (rate * seconds).toInt()
    val data = ByteArray(frames * 4)
    var o = 0
    for (i in 0 until frames) {
        val f = freq * (1 + (i / (rate * 2)) % 3)
        val v = (sin(2.0 * PI * f * i / rate) * 12000 * (0.4 + 0.6 * sin(2.0 * PI * i / (rate * seconds)))).toInt()
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
}

private fun writeCoverPng(file: File, label: String, variant: Int) {
    val palettes = listOf(
        Color(0xFF7C5CFF.toInt()) to Color(0xFF241E4E.toInt()),
        Color(0xFFFF8A3D.toInt()) to Color(0xFF4E2A1E.toInt()),
        Color(0xFF2F80ED.toInt()) to Color(0xFF12294E.toInt()),
        Color(0xFFF43F5E.toInt()) to Color(0xFF3D0F1F.toInt()),
        Color(0xFF10B981.toInt()) to Color(0xFF0B3D31.toInt()),
        Color(0xFFC084FC.toInt()) to Color(0xFF2D1B4E.toInt()),
    )
    val (a, b) = palettes[variant % palettes.size]
    val img = BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    g.paint = GradientPaint(0f, 0f, a, 512f, 512f, b)
    g.fillRect(0, 0, 512, 512)

    val shiftX = ((variant % 3) - 1) * 38.0
    val shiftY = (((variant / 2) % 3) - 1) * 30.0
    g.color = Color(1f, 1f, 1f, 0.13f)
    g.fill(Ellipse2D.Double(-70.0 + shiftX, -60.0 + shiftY, 340.0, 340.0))
    g.color = Color(1f, 1f, 1f, 0.08f)
    g.fill(Ellipse2D.Double(230.0 - shiftX, 210.0 - shiftY, 360.0, 360.0))

    g.color = Color(1f, 1f, 1f, 0.18f)
    g.stroke = BasicStroke(3f)
    g.draw(RoundRectangle2D.Double(28.0, 28.0, 456.0, 456.0, 48.0, 48.0))
    g.draw(Ellipse2D.Double(116.0, 116.0, 280.0, 280.0))

    g.color = Color(1f, 1f, 1f, 0.34f)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 240)
    val text = label.take(1).uppercase()
    val fm = g.fontMetrics
    g.drawString(text, 512 - fm.stringWidth(text) - 44, 512 - fm.descent - 28)

    g.dispose()
    ImageIO.write(img, "png", file)
}

private fun writeLrc(file: File, title: String) {
    val lines = buildString {
        appendLine("[ti:$title]")
        for (i in 0 until 24) {
            val t = 3_500L * i
            appendLine("[%02d:%05.2f]%s %d".format(t / 60_000, (t % 60_000) / 1000.0, title, i + 1))
        }
    }
    file.writeText(lines)
}
