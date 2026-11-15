package com.rabbeat.app.desktop

import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.TrackOrigin
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

class LibraryScanner(private val db: RabBeatDatabase) {

    private val artDir = File(dataDir(), "art")
    private var running = false

    suspend fun scan(root: File, onProgress: (done: Int, total: Int) -> Unit): Int = withContext(Dispatchers.IO) {
        if (running) return@withContext 0
        running = true
        try {
            db.sourceDao().upsert(localSource())
            val files = root.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
                .toList()
            val now = System.currentTimeMillis()
            val albums = linkedMapOf<String, AlbumEntity>()
            val artists = linkedMapOf<String, ArtistEntity>()
            val batch = mutableListOf<TrackEntity>()
            var done = 0
            files.forEach { file ->
                entityFor(file, root, now)?.let { entity ->
                    batch += entity
                    mergeAlbum(albums, entity)
                    mergeArtist(artists, entity)
                }
                done++
                if (done % 25 == 0) onProgress(done, files.size)
            }
            onProgress(files.size, files.size)
            db.trackDao().upsertAll(batch)
            db.albumDao().upsertAll(albums.values.toList())
            db.artistDao().upsertAll(artists.values.toList())
            DesktopLog.i("Scanner", "scan ${root.absolutePath}: ${batch.size} tracks imported")
            batch.size
        } finally {
            running = false
        }
    }

    /** Drop DB rows whose backing file disappeared (moved/deleted outside the app). */
    suspend fun prune(): Int = withContext(Dispatchers.IO) {
        val stale = db.trackDao()
            .query(androidx.room.RoomRawQuery("SELECT * FROM tracks"))
            .filter { track ->
                track.uri.startsWith("file:") &&
                    runCatching { !java.io.File(java.net.URI(track.uri)).exists() }.getOrDefault(true)
            }
        if (stale.isNotEmpty()) db.trackDao().deleteByIds(stale.map { it.id })
        stale.size
    }

    private fun entityFor(file: File, root: File, now: Long): TrackEntity? = runCatching {
        val id = "dsk_" + sha1(file.absolutePath).take(24)
        val read = runCatching { AudioFileIO.read(file) }.getOrNull()
        val tag = read?.tag
        val header = read?.audioHeader
        val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() } ?: "未知艺人"
        val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() } ?: "未知专辑"
        val artwork = tag?.firstArtwork?.binaryData?.let { bytes ->
            val out = File(artDir.apply { mkdirs() }, "$id.jpg")
            runCatching { out.writeBytes(bytes) }.map { out.toURI().toString() }.getOrNull()
        }
        TrackEntity(
            id = id,
            sourceId = LOCAL_SOURCE_ID,
            remoteId = null,
            title = title,
            artist = artist,
            artistId = "dart_" + sha1(artist).take(16),
            album = album,
            albumId = "dalb_" + sha1("$artist|$album").take(16),
            durationMs = (header?.trackLength ?: 0) * 1000L,
            trackNumber = tag?.getFirst(FieldKey.TRACK)?.toIntOrNull(),
            discNumber = tag?.getFirst(FieldKey.DISC_NO)?.toIntOrNull(),
            year = tag?.getFirst(FieldKey.YEAR)?.toIntOrNull(),
            mimeType = mimeOf(file),
            bitrate = header?.bitRateAsNumber?.toInt(),
            sampleRate = header?.sampleRateAsNumber,
            fileSize = file.length(),
            uri = file.toURI().toString(),
            artworkUri = artwork,
            origin = TrackOrigin.LOCAL,
            downloadState = DownloadState.NONE,
            isFavorite = false,
            playCount = 0,
            lastPlayedAt = null,
            addedAt = now,
            folder = file.parentFile?.absolutePath?.removePrefix(root.absolutePath)?.trimStart(File.separatorChar),
        )
    }.getOrNull()

    private fun mergeAlbum(albums: MutableMap<String, AlbumEntity>, track: TrackEntity) {
        val id = track.albumId ?: return
        val current = albums[id]
        albums[id] = AlbumEntity(
            id = id,
            sourceId = LOCAL_SOURCE_ID,
            title = track.album,
            artist = track.artist,
            year = track.year,
            artworkUri = current?.artworkUri ?: track.artworkUri,
            trackCount = (current?.trackCount ?: 0) + 1,
            addedAt = track.addedAt,
        )
    }

    private fun mergeArtist(artists: MutableMap<String, ArtistEntity>, track: TrackEntity) {
        val id = track.artistId ?: return
        val current = artists[id]
        artists[id] = ArtistEntity(
            id = id,
            sourceId = LOCAL_SOURCE_ID,
            name = track.artist,
            artworkUri = current?.artworkUri ?: track.artworkUri,
            albumCount = (current?.albumCount ?: 0) + 1,
            trackCount = (current?.trackCount ?: 0) + 1,
        )
    }

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "ncm" -> "audio/x-ncm"
        "mgg", "qmc", "mflac", "tkm", "ofl_en", "ofc" -> "audio/x-qmc"
        else -> "audio/*"
    }

    private fun localSource() = SourceEntity(
        id = LOCAL_SOURCE_ID,
        type = SourceType.LOCAL,
        displayName = "本地文件",
        configJson = "{}",
        status = SourceStatus.CONNECTED,
        lastSyncAt = null,
        createdAt = System.currentTimeMillis(),
    )

    companion object {
        const val LOCAL_SOURCE_ID = "dsk_local"
        private val EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "wav", "aif", "aiff", "au",
            "ncm", "mgg", "qmc", "mflac", "tkm", "ofl_en", "ofc",
        )

        fun dataDir(): File = File(System.getProperty("user.home"), ".rabbeat")

        private fun sha1(text: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
