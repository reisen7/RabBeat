package com.rabbeat.app.desktop.library
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

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
import com.rabbeat.app.util.NcmDecoderCore
import com.rabbeat.app.util.QmcDecoderCore
import javax.sound.sampled.AudioSystem
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

class LibraryScanner(
    private val db: RabBeatDatabase,
    private val settings: DesktopSettings = DesktopSettings(),
) {

    private fun artDir(): File = settings.artRoot().apply { mkdirs() }
    private var running = false

    suspend fun scan(root: File, onProgress: (done: Int, total: Int) -> Unit): Int = withContext(Dispatchers.IO) {
        if (running) return@withContext 0
        running = true
        try {
            db.sourceDao().upsert(localSource())
            val known = db.trackDao().getBySource(LOCAL_SOURCE_ID).associateBy { it.id }
            val files = root.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
                .toList()
            val now = System.currentTimeMillis()
            val albums = linkedMapOf<String, AlbumEntity>()
            val artists = linkedMapOf<String, ArtistEntity>()
            val batch = mutableListOf<TrackEntity>()
            var done = 0
            files.forEach { file ->
                entityFor(file, root, now, known)?.let { entity ->
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

    private fun entityFor(file: File, root: File, now: Long, known: Map<String, TrackEntity>): TrackEntity? = runCatching {
        val id = "dsk_" + sha1(file.absolutePath).take(24)
        val previous = known[id]
        // VIP/encrypted containers (.ncm/.qmc family) are decrypted once into the
        // user's music dir under a readable name, then imported as normal audio
        // so tags/seek/duration work. Decoding is cached in the cache dir, so
        // rescans only parse the container header.
        val converted = if (file.extension.lowercase() in ENCRYPTED_EXTENSIONS) {
            convertEncrypted(file)
        } else {
            null
        }
        val audioFile = converted?.file ?: file
        val read = runCatching { AudioFileIO.read(audioFile) }.getOrNull()
        val tag = read?.tag
        val header = read?.audioHeader
        val ncmTags = converted?.tags
        val title = ncmTags?.title ?: tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension
        val artist = ncmTags?.artist ?: tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() } ?: "未知艺人"
        val album = ncmTags?.album ?: tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() } ?: "未知专辑"
        val coverBytes = ncmTags?.cover ?: tag?.firstArtwork?.binaryData
        val artwork = coverBytes?.let { bytes ->
            val out = File(artDir(), "$id.jpg")
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
            durationMs = ncmTags?.durationMs
                ?: header?.trackLength?.takeIf { it > 0 }?.times(1000L)
                ?: probeDurationMs(audioFile)
                ?: 0L,
            trackNumber = tag?.getFirst(FieldKey.TRACK)?.toIntOrNull(),
            discNumber = tag?.getFirst(FieldKey.DISC_NO)?.toIntOrNull(),
            year = tag?.getFirst(FieldKey.YEAR)?.toIntOrNull(),
            mimeType = converted?.mimeType ?: mimeOf(audioFile),
            bitrate = ncmTags?.bitrate ?: header?.bitRateAsNumber?.toInt(),
            sampleRate = header?.sampleRateAsNumber,
            fileSize = audioFile.length(),
            uri = audioFile.toURI().toString(),
            artworkUri = artwork ?: previous?.artworkUri,
            origin = TrackOrigin.LOCAL,
            downloadState = DownloadState.NONE,
            isFavorite = previous?.isFavorite ?: false,
            playCount = previous?.playCount ?: 0,
            lastPlayedAt = previous?.lastPlayedAt,
            addedAt = previous?.addedAt ?: now,
            lyricUri = previous?.lyricUri,
            lyricOffsetMs = previous?.lyricOffsetMs ?: 0,
            folder = file.parentFile?.absolutePath?.removePrefix(root.absolutePath)?.trimStart(File.separatorChar),
            hidden = previous?.hidden ?: false,
            contentKey = previous?.contentKey,
            syncId = previous?.syncId,
        )
    }.getOrNull()

    private data class Converted(val file: File, val mimeType: String?, val tags: NcmDecoderCore.Tags?)

    private var decodeDirCached: File? = null
    private var ncmDecoder: NcmDecoderCore? = null
    private var qmcDecoder: QmcDecoderCore? = null

    private fun decoders(dir: File): Pair<NcmDecoderCore, QmcDecoderCore> {
        if (decodeDirCached != dir) {
            decodeDirCached = dir
            ncmDecoder = NcmDecoderCore(dir)
            qmcDecoder = QmcDecoderCore(dir)
        }
        return ncmDecoder!! to qmcDecoder!!
    }

    /**
     * Decrypts an ncm/qmc-family file. The decoders write hash-named output into
     * the cache dir; we then copy it into the music dir with a readable name
     * ("title - artist-ab12cd.mp3") so the folder is usable outside the app.
     * Repeat scans hit the decoder's own cache → only the container header is read.
     */
    private fun convertEncrypted(file: File): Converted? = runCatching {
        val musicDir = settings.convertRoot().apply { mkdirs() }
        val decodeDir = File(settings.cacheRoot(), "decoded").apply { mkdirs() }
        val key = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val (ncm, qmc) = decoders(decodeDir)
        when (file.extension.lowercase()) {
            "ncm" -> {
                val d = file.inputStream().buffered().use { ncm.decode(it, key) } ?: return null
                Converted(placeDecoded(file, musicDir, d.file), d.mimeType, d.tags)
            }
            else -> {
                val d = file.inputStream().channel.use { qmc.decode(it, key) } ?: return null
                Converted(placeDecoded(file, musicDir, d.file), d.mimeType, null)
            }
        }
    }.getOrNull()

    private fun placeDecoded(source: File, musicDir: File, decoded: File): File {
        val pretty = File(musicDir, "${prettyName(source)}.${decoded.extension}")
        if ((!pretty.isFile || pretty.length() != decoded.length()) &&
            !decoded.absoluteFile.equals(pretty.absoluteFile)
        ) {
            runCatching { decoded.copyTo(pretty, overwrite = true) }
        }
        return if (pretty.isFile && pretty.length() > 0) pretty else decoded
    }

    private fun prettyName(file: File): String {
        val clean = file.nameWithoutExtension
            .replace(Regex("""[<>:"/\\|?* -]"""), "_")
            .trim()
            .trimEnd('.')
            .take(80)
            .ifBlank { "track" }
        // Short source-hash suffix keeps same-named files from colliding and
        // makes the name deterministic across rescans.
        return "$clean-${sha1(file.absolutePath).take(6)}"
    }

    /** Duration via the SPI audio-file-format "duration" property (µs) — works on files whose tags jaudiotagger rejects. */
    private fun probeDurationMs(file: File): Long? = runCatching {
        (AudioSystem.getAudioFileFormat(file).properties()["duration"] as? Number)
            ?.toLong()?.div(1000L)?.takeIf { it > 0 }
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
        private val ENCRYPTED_EXTENSIONS = setOf("ncm", "mgg", "qmc", "mflac", "tkm", "ofl_en", "ofc")

        private val EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "wav", "aif", "aiff", "au",
        ) + ENCRYPTED_EXTENSIONS

        fun dataDir(): File {
            System.getProperty("rabbeat.dataDir")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            System.getenv("RABBEAT_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { return File(it) }
            return File(System.getProperty("user.home"), ".rabbeat")
        }

        private fun sha1(text: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
