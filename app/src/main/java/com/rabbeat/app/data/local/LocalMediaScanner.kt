package com.rabbeat.app.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import java.io.File
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.ScanSettings
import com.rabbeat.app.domain.model.isNonSongFolder
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.SyncSummary
import com.rabbeat.app.domain.model.TrackOrigin
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.util.LyricReader
import com.rabbeat.app.util.Mp3DurationReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RabBeatDatabase,
    private val mp3Duration: Mp3DurationReader,
    private val lyrics: LyricReader,
    private val settings: SettingsRepository,
    private val netease: NeteaseReconciler,
) {
    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun importDeviceLibrary(): SyncSummary = withContext(Dispatchers.IO) {
        if (!hasAudioPermission()) {
            return@withContext SyncSummary(trackCount = 0, albumCount = 0, missingMetadata = 0)
        }
        val filters = settings.scanSettings.first()
        val lyricIndex = indexSidecars()
        val rows = queryAll(filters, lyricIndex)
        val now = System.currentTimeMillis()
        val summary = database.withTransaction {
            val trackDao = database.trackDao()
            val existing = trackDao.getBySource(SOURCE_ID).associateBy { it.id }
            val others = trackDao.getOtherSources(SOURCE_ID)
                .filter { it.sourceId != NETEASE_SOURCE_ID }
                .map { it.toCandidate() }
            val plan = TrackDeduper.plan(rows.map { it.toCandidate() }, existing.keys, others)
            val kept = rows.filter { it.id in plan.keptIds }
            val losersByWinner = mutableMapOf<String, MutableList<TrackEntity>>()
            plan.replaced.forEach { (loser, winner) ->
                val entity = existing[loser]
                if (entity != null) {
                    trackDao.reassignPlaylistTracks(loser, winner)
                    trackDao.removePlaylistTracks(loser)
                    trackDao.reassignPlayHistory(loser, winner)
                    trackDao.reassignLyricLinks(loser, winner)
                    trackDao.removeLyricLink(loser)
                    trackDao.reassignPlaybackState(loser, winner)
                    if (winner in plan.keptIds) {
                        losersByWinner.getOrPut(winner) { mutableListOf() } += entity
                    } else {
                        trackDao.mergeStatsInto(winner, entity.isFavorite, entity.playCount, entity.lastPlayedAt, entity.addedAt)
                    }
                }
            }
            val previous = database.sourceDao().get(SOURCE_ID)
            database.sourceDao().upsert(
                SourceEntity(
                    id = SOURCE_ID,
                    type = SourceType.LOCAL,
                    displayName = "这台手机",
                    configJson = """{"root":"mediastore"}""",
                    status = SourceStatus.CONNECTED,
                    lastSyncAt = now,
                    createdAt = previous?.createdAt ?: now,
                ),
            )
            if (kept.isEmpty()) {
                trackDao.deleteBySource(SOURCE_ID)
            } else {
                val keep = kept.map { it.id }.toSet()
                existing.keys.filter { it !in keep }.chunked(400).forEach { stale ->
                    trackDao.deleteByIds(stale)
                }
            }
            val tracks = kept.map { row ->
                val preserved = existing[row.id]
                val losers = losersByWinner[row.id].orEmpty()
                TrackEntity(
                    id = row.id,
                    sourceId = SOURCE_ID,
                    remoteId = row.remoteId,
                    title = row.title,
                    artist = row.artist,
                    artistId = row.artistId,
                    album = row.album,
                    albumId = row.albumId,
                    durationMs = row.durationMs,
                    trackNumber = row.trackNumber,
                    discNumber = row.discNumber,
                    year = row.year,
                    mimeType = row.mimeType,
                    bitrate = null,
                    sampleRate = null,
                    fileSize = row.size,
                    uri = row.uri,
                    artworkUri = row.artworkUri,
                    origin = TrackOrigin.LOCAL,
                    downloadState = DownloadState.DOWNLOADED,
                    isFavorite = (preserved?.isFavorite ?: false) || losers.any { it.isFavorite },
                    playCount = maxOf(preserved?.playCount ?: 0, losers.maxOfOrNull { it.playCount } ?: 0),
                    lastPlayedAt = listOfNotNull(preserved?.lastPlayedAt, losers.mapNotNull { it.lastPlayedAt }.maxOrNull()).maxOrNull(),
                    addedAt = listOfNotNull(preserved?.addedAt, losers.minOfOrNull { it.addedAt }).minOrNull() ?: row.addedAt,
                    lyricUri = row.lyricUri ?: preserved?.lyricUri,
                    folder = row.folder,
                )
            }
            tracks.chunked(200).forEach { trackDao.upsertAll(it) }
            database.albumDao().deleteBySource(SOURCE_ID)
            database.artistDao().deleteBySource(SOURCE_ID)
            val albums = kept.groupBy { it.albumId }.map { (id, songs) ->
                val first = songs.first()
                AlbumEntity(
                    id = id,
                    sourceId = SOURCE_ID,
                    title = first.album,
                    artist = first.artist,
                    year = songs.mapNotNull { it.year }.maxOrNull(),
                    artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
                    trackCount = songs.size,
                    addedAt = songs.minOf { it.addedAt },
                )
            }
            val artists = kept.groupBy { it.artistId }.map { (id, songs) ->
                ArtistEntity(
                    id = id,
                    sourceId = SOURCE_ID,
                    name = songs.first().artist,
                    artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
                    albumCount = songs.map { it.albumId }.distinct().size,
                    trackCount = songs.size,
                )
            }
            albums.chunked(200).forEach { database.albumDao().upsertAll(it) }
            artists.chunked(200).forEach { database.artistDao().upsertAll(it) }
            SyncSummary(
                trackCount = kept.size,
                albumCount = albums.size,
                missingMetadata = kept.count { it.artworkUri == null || it.year == null },
            )
        }
        netease.reconcile()
        summary
    }

    private fun queryAll(filters: ScanSettings, lyricIndex: Map<String, LyricHit>): List<Scanned> {
        if (!hasAudioPermission()) return emptyList()
        val found = linkedMapOf<String, Scanned>()
        volumes().forEach { (volume, audioUri) ->
            query(audioUri, volume, audioColumns(), fromAudioTable = true, filters, lyricIndex, found)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                query(MediaStore.Files.getContentUri(volume), volume, fileColumns(), fromAudioTable = false, filters, lyricIndex, found)
            }
        }
        return found.values.toList()
    }

    private fun volumes(): List<Pair<String, Uri>> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).map { name ->
                name to MediaStore.Audio.Media.getContentUri(name)
            }
        } else {
            listOf("external" to MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }
    }

    private fun query(
        collection: Uri,
        volume: String,
        projection: Array<String>,
        fromAudioTable: Boolean,
        filters: ScanSettings,
        lyricIndex: Map<String, LyricHit>,
        into: MutableMap<String, Scanned>,
    ) {
        val selection = if (fromAudioTable) {
            "${MediaStore.MediaColumns.SIZE} >= ?"
        } else {
            "${MediaStore.MediaColumns.SIZE} >= ? AND (" +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}" +
                " OR ${MediaStore.MediaColumns.MIME_TYPE} LIKE 'audio/%')"
        }
        val minBytes = if (filters.skipSmallFiles) filters.minSizeBytes else 1L
        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                arrayOf(minBytes.toString()),
                "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE",
            )
        }.getOrNull() ?: return
        cursor.use { rows ->
            while (rows.moveToNext()) {
                val name = rows.text(MediaStore.MediaColumns.DISPLAY_NAME) ?: continue
                if (name.startsWith(".")) continue
                val mime = rows.text(MediaStore.MediaColumns.MIME_TYPE)
                if (!isAudio(name, mime)) continue
                val data = rows.text(MediaStore.MediaColumns.DATA)
                val relative = rows.text(MediaStore.MediaColumns.RELATIVE_PATH)
                if (filters.filterNonSongs && isNonSong(rows, fromAudioTable, relative, data)) continue
                val dedupe = identity(relative, data, name)
                if (!fromAudioTable && into.containsKey(dedupe)) continue
                val mediaId = rows.number(MediaStore.MediaColumns._ID)
                if (mediaId <= 0L) continue
                val title = displayTitle(rows.text(MediaStore.MediaColumns.TITLE), name)
                val artist = known(rows.text(MediaStore.Audio.Media.ARTIST), "未知艺术家")
                val album = known(rows.text(MediaStore.Audio.Media.ALBUM), "未知专辑")
                val albumStoreId = rows.number(MediaStore.Audio.Media.ALBUM_ID)
                val artistStoreId = rows.number(MediaStore.Audio.Media.ARTIST_ID)
                val rawTrack = rows.number(MediaStore.Audio.Media.TRACK).toInt()
                val trackNumber = (rawTrack % 1000).takeIf { it > 0 }
                val discNumber = (rawTrack / 1000).takeIf { it > 0 }
                val year = rows.number(MediaStore.Audio.Media.YEAR).toInt().takeIf { it > 0 }
                val addedSeconds = rows.number(MediaStore.MediaColumns.DATE_ADDED)
                val uri = ContentUris.withAppendedId(collection, mediaId).toString()
                val folder = matchFolder(relative, data).ifBlank { null }
                val trackId = "dev_${volume}_$mediaId"
                val artwork = if (fromAudioTable && albumStoreId > 0L) {
                    ContentUris.withAppendedId(ALBUM_ART, albumStoreId).toString()
                } else {
                    null
                }
                val storeDuration = rows.number(MediaStore.Audio.Media.DURATION).coerceAtLeast(0L)
                val measured = if (isMp3(name, mime)) mp3Duration.readMillis(uri) else null
                val durationMs = measured?.takeIf { it >= 500L } ?: storeDuration
                if (filters.rejectsDuration(durationMs)) continue
                into[dedupe] = Scanned(
                    id = trackId,
                    remoteId = "$volume:$mediaId",
                    title = title,
                    artist = artist,
                    artistId = if (artistStoreId > 0L) "devart_$artistStoreId" else "devart_${artist.lowercase()}",
                    album = album,
                    albumId = if (albumStoreId > 0L) "devalb_$albumStoreId" else "devalb_${artist.lowercase()}_${album.lowercase()}",
                    durationMs = durationMs,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    year = year,
                    mimeType = mime,
                    size = rows.number(MediaStore.MediaColumns.SIZE),
                    uri = uri,
                    artworkUri = artwork,
                    addedAt = if (addedSeconds > 0L) addedSeconds * 1000L else System.currentTimeMillis(),
                    lyricUri = attachLyric(trackId, uri, data, name, folder, lyricIndex),
                    folder = folder,
                )
            }
        }
    }

    private fun audioColumns(): Array<String> {
        val columns = baseColumns() + arrayOf(
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns + MediaStore.Audio.Media.IS_PODCAST
        } else {
            columns
        }
    }

    private fun fileColumns(): Array<String> = baseColumns()

    private fun baseColumns(): Array<String> {
        val columns = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DATA,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns += MediaStore.MediaColumns.RELATIVE_PATH
        }
        return columns.toTypedArray()
    }

    private fun android.database.Cursor.text(column: String): String? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getString(index)?.takeIf { it.isNotBlank() }
    }

    private fun android.database.Cursor.number(column: String): Long {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return 0L
        return getLong(index)
    }

    private data class Scanned(
        val id: String,
        val remoteId: String,
        val title: String,
        val artist: String,
        val artistId: String,
        val album: String,
        val albumId: String,
        val durationMs: Long,
        val trackNumber: Int?,
        val discNumber: Int?,
        val year: Int?,
        val mimeType: String?,
        val size: Long,
        val uri: String,
        val artworkUri: String?,
        val addedAt: Long,
        val lyricUri: String?,
        val folder: String?,
    )

    private fun Scanned.toCandidate(): DupCandidate = DupCandidate(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        fileSize = size,
        mimeType = mimeType,
        folder = folder,
    )

    private fun isNonSong(rows: android.database.Cursor, fromAudioTable: Boolean, relative: String?, data: String?): Boolean {
        if (filtersPath(relative) || filtersPath(data)) return true
        if (!fromAudioTable) return false
        if (rows.flag(MediaStore.Audio.Media.IS_RINGTONE) == 1) return true
        if (rows.flag(MediaStore.Audio.Media.IS_NOTIFICATION) == 1) return true
        if (rows.flag(MediaStore.Audio.Media.IS_ALARM) == 1) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && rows.flag(MediaStore.Audio.Media.IS_PODCAST) == 1) return true
        return false
    }

    private fun filtersPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return path.split('/', '\\').any(::isNonSongFolder)
    }

    private fun indexSidecars(): Map<String, LyricHit> {
        if (!hasAudioPermission()) return emptyMap()
        val found = linkedMapOf<String, LyricHit>()
        volumes().forEach { (volume, _) ->
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(volume)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val projection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection += MediaStore.MediaColumns.RELATIVE_PATH
            }
            val cursor = runCatching {
                context.contentResolver.query(
                    collection,
                    projection.toTypedArray(),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("%.lrc%"),
                    null,
                )
            }.getOrNull() ?: return@forEach
            cursor.use { rows ->
                while (rows.moveToNext()) {
                    val name = rows.text(MediaStore.MediaColumns.DISPLAY_NAME) ?: continue
                    if (!isLrcName(name)) continue
                    val id = rows.number(MediaStore.MediaColumns._ID)
                    if (id <= 0L) continue
                    val relative = rows.text(MediaStore.MediaColumns.RELATIVE_PATH)
                    val data = rows.text(MediaStore.MediaColumns.DATA)
                    val hit = LyricHit(
                        uri = ContentUris.withAppendedId(collection, id),
                        path = data,
                    )
                    registerLyric(found, matchFolder(relative, data), lyricStem(name), hit)
                }
            }
        }
        return found
    }

    private fun attachLyric(
        id: String,
        audioUri: String,
        data: String?,
        name: String,
        folder: String?,
        index: Map<String, LyricHit>,
    ): String? {
        val stem = name.substringBeforeLast('.').lowercase()
        val hit = index[lyricKey(folder, stem)]
        if (hit != null) {
            lyrics.store(id, hit.uri)?.let { return it }
            val path = hit.path
            if (!path.isNullOrBlank()) {
                lyrics.store(id, Uri.fromFile(File(path)))?.let { return it }
            }
        }
        fileSidecar(data, name)?.let { file ->
            lyrics.store(id, Uri.fromFile(file))?.let { return it }
        }
        return lyrics.read(audioUri)?.let { lyrics.storeText(id, it) }
    }

    private fun fileSidecar(data: String?, name: String): File? {
        val parent = data?.substringBeforeLast('/', "")?.takeIf { it.isNotBlank() } ?: return null
        val stem = name.substringBeforeLast('.')
        val candidates = listOf(
            File(parent, "$stem.lrc"),
            File(parent, "$name.lrc"),
            File(parent, "lrc/$stem.lrc"),
            File(parent, "lyrics/$stem.lrc"),
            File(parent, "歌词/$stem.lrc"),
        )
        return candidates.firstOrNull { it.isFile && it.canRead() }
    }

    private fun android.database.Cursor.flag(column: String): Int? {
        val index = getColumnIndex(column)
        if (index < 0 || isNull(index)) return null
        return getInt(index)
    }

    private data class LyricHit(val uri: Uri, val path: String?)

    companion object {
        const val SOURCE_ID = "src_device"
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
        private val LYRIC_DIRS = setOf("lrc", "lyric", "lyrics", "歌词")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "ape", "mka", "aiff", "aif")

        private fun identity(relative: String?, data: String?, name: String): String {
            val folder = relative?.trim()?.trim('/')?.lowercase().orEmpty()
            if (folder.isNotEmpty()) return "$folder/${name.lowercase()}"
            val path = data?.trim()?.lowercase().orEmpty()
            if (path.isNotEmpty()) return path
            return name.lowercase()
        }

        private fun displayTitle(title: String?, fileName: String): String {
            val raw = title?.trim().orEmpty()
            val fromFile = fileName.substringBeforeLast('.').ifBlank { fileName }
            if (raw.isEmpty() || raw.equals("<unknown>", true)) return fromFile
            return raw
        }

        private fun known(value: String?, fallback: String): String {
            val raw = value?.trim().orEmpty()
            if (raw.isEmpty() || raw.equals("<unknown>", true) || raw == "未知") return fallback
            return raw
        }

        private fun isAudio(name: String, mime: String?): Boolean {
            if (mime?.startsWith("audio/") == true) return true
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in AUDIO_EXT
        }

        private fun isLrcName(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".lrc") || lower.endsWith(".lrc.txt")
        }

        private fun lyricStem(name: String): String {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".lrc.txt") -> lower.removeSuffix(".lrc.txt")
                lower.endsWith(".lrc") -> lower.removeSuffix(".lrc")
                else -> lower.substringBeforeLast('.')
            }
        }

        private fun matchFolder(relative: String?, data: String?): String {
            if (!relative.isNullOrBlank()) {
                val trimmed = relative.trim().trim('/')
                val stripped = stripLyricDir(trimmed)
                return stripped.ifBlank { trimmed }
            }
            val parent = data?.substringBeforeLast('/', "")?.trim()?.trim('/') ?: return ""
            if (parent.isEmpty()) return ""
            val leaf = parent.substringAfterLast('/')
            if (LYRIC_DIRS.any { leaf.equals(it, ignoreCase = true) }) {
                val above = parent.substringBeforeLast('/', "")
                return above.substringAfterLast('/').ifBlank { above }
            }
            return leaf
        }

        private fun stripLyricDir(folder: String): String {
            val leaf = folder.substringAfterLast('/')
            if (LYRIC_DIRS.any { leaf.equals(it, ignoreCase = true) }) {
                return folder.substringBeforeLast('/', "")
            }
            return folder
        }

        private fun lyricKey(folder: String?, stem: String): String {
            return "${folder?.trim('/')?.lowercase().orEmpty()}\u0000${stem.lowercase()}"
        }

        private fun registerLyric(map: MutableMap<String, LyricHit>, folder: String, stem: String, hit: LyricHit) {
            map.putIfAbsent(lyricKey(folder, stem), hit)
        }

        private fun isMp3(name: String, mime: String?): Boolean {
            val type = mime?.lowercase().orEmpty()
            return type == "audio/mpeg" || type == "audio/mp3" || name.endsWith(".mp3", ignoreCase = true)
        }
    }
}
