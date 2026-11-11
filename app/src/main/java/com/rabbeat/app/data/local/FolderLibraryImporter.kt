package com.rabbeat.app.data.local

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.ScanSettings
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.SyncSummary
import com.rabbeat.app.domain.model.TrackOrigin
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.util.LyricReader
import com.rabbeat.app.util.Mp3DurationReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Singleton
class FolderLibraryImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: RabBeatDatabase,
    private val mp3Duration: Mp3DurationReader,
    private val lyrics: LyricReader,
    private val settings: SettingsRepository,
    private val netease: NeteaseReconciler,
) {
    suspend fun importTree(tree: Uri): SyncSummary = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(tree, flags) }
        val sourceId = sourceId(tree)
        val name = folderName(tree)
        write(sourceId, name, tree)
    }

    suspend fun rescanSaved(): SyncSummary = withContext(Dispatchers.IO) {
        val folders = database.sourceDao().getAll().filter { it.id.startsWith(PREFIX) }
        var tracks = 0
        var albums = 0
        var missing = 0
        folders.forEach { source ->
            val root = runCatching { json.decodeFromString(FolderConfig.serializer(), source.configJson).root }.getOrNull() ?: return@forEach
            val summary = write(source.id, source.displayName, Uri.parse(root))
            tracks += summary.trackCount
            albums += summary.albumCount
            missing += summary.missingMetadata
        }
        SyncSummary(tracks, albums, missing)
    }

    private suspend fun write(sourceId: String, name: String, tree: Uri): SyncSummary {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return SyncSummary(0, 0, 0)
        val filters = settings.scanSettings.first()
        val audio = mutableListOf<FoundAudio>()
        val lrcs = linkedMapOf<String, Uri>()
        val rootName = root.name?.trim().orEmpty().ifBlank { folderName(tree) }
        walk(root, rootName, filters, audio, lrcs)
        val now = System.currentTimeMillis()
            val rows = audio.mapNotNull { file -> readFile(sourceId, file, lrcs, filters, now) }
        val summary = database.withTransaction {
            val trackDao = database.trackDao()
            val existing = trackDao.getBySource(sourceId).associateBy { it.id }
            val others = trackDao.getOtherSources(sourceId)
                .filter { it.sourceId != NETEASE_SOURCE_ID }
                .map { it.toCandidate() }
            val plan = TrackDeduper.plan(rows.map { it.entity.toCandidate() }, existing.keys, others)
            val kept = rows.filter { it.entity.id in plan.keptIds }
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
            val previous = database.sourceDao().get(sourceId)
            database.sourceDao().upsert(
                SourceEntity(
                    id = sourceId,
                    type = SourceType.LOCAL,
                    displayName = name,
                    configJson = json.encodeToString(FolderConfig.serializer(), FolderConfig(tree.toString())),
                    status = SourceStatus.CONNECTED,
                    lastSyncAt = now,
                    createdAt = previous?.createdAt ?: now,
                ),
            )
            if (kept.isEmpty()) {
                trackDao.deleteBySource(sourceId)
            } else {
                val keep = kept.map { it.entity.id }.toSet()
                existing.keys.filter { id -> id !in keep }.chunked(400).forEach { ids -> trackDao.deleteByIds(ids) }
            }
            val tracks = kept.map { row ->
                val preserved = existing[row.entity.id]
                val losers = losersByWinner[row.entity.id].orEmpty()
                row.entity.copy(
                    isFavorite = (preserved?.isFavorite ?: false) || losers.any { it.isFavorite },
                    playCount = maxOf(preserved?.playCount ?: 0, losers.maxOfOrNull { it.playCount } ?: 0),
                    lastPlayedAt = listOfNotNull(preserved?.lastPlayedAt, losers.mapNotNull { it.lastPlayedAt }.maxOrNull()).maxOrNull(),
                    addedAt = listOfNotNull(preserved?.addedAt, losers.minOfOrNull { it.addedAt }).minOrNull() ?: row.entity.addedAt,
                )
            }
            tracks.chunked(200).forEach { trackDao.upsertAll(it) }
            database.albumDao().deleteBySource(sourceId)
            database.artistDao().deleteBySource(sourceId)
            val albums = kept.groupBy { it.entity.albumId }.map { (id, songs) ->
                val first = songs.first().entity
                AlbumEntity(
                    id = id ?: "fldalb_${sourceId}_unknown",
                    sourceId = sourceId,
                    title = first.album,
                    artist = first.artist,
                    year = songs.mapNotNull { it.entity.year }.maxOrNull(),
                    artworkUri = songs.firstNotNullOfOrNull { it.entity.artworkUri },
                    trackCount = songs.size,
                    addedAt = songs.minOf { it.entity.addedAt },
                )
            }
            val artists = kept.groupBy { it.entity.artistId }.map { (id, songs) ->
                val first = songs.first().entity
                ArtistEntity(
                    id = id ?: "fldart_${sourceId}_unknown",
                    sourceId = sourceId,
                    name = first.artist,
                    artworkUri = songs.firstNotNullOfOrNull { it.entity.artworkUri },
                    albumCount = songs.map { it.entity.albumId }.distinct().size,
                    trackCount = songs.size,
                )
            }
            albums.chunked(200).forEach { database.albumDao().upsertAll(it) }
            artists.chunked(200).forEach { database.artistDao().upsertAll(it) }
            SyncSummary(
                trackCount = kept.size,
                albumCount = kept.map { it.entity.albumId }.distinct().size,
                missingMetadata = kept.count { it.entity.artworkUri == null || it.entity.year == null },
            )
        }
        netease.reconcile()
        return summary
    }

    private fun walk(
        dir: DocumentFile,
        folder: String,
        filters: ScanSettings,
        audio: MutableList<FoundAudio>,
        lrcs: MutableMap<String, Uri>,
    ) {
        val children = dir.listFiles()
        if (children.any { it.name == ".nomedia" }) return
        val current = dir.name?.trim().orEmpty()
        if (filters.rejectsPath(current)) return
        children.forEach { child ->
            val childName = child.name ?: return@forEach
            if (childName.startsWith(".")) return@forEach
            when {
                child.isDirectory && isLyricFolder(childName) -> indexLyrics(child, folder, lrcs)
                child.isDirectory -> walk(child, joinFolder(folder, childName), filters, audio, lrcs)
                isLrc(childName) -> putLrc(lrcs, folder, childName, child.uri)
                child.isFile && isAudio(child) -> audio += FoundAudio(child, folder.ifBlank { "本地" })
            }
        }
    }

    private fun indexLyrics(dir: DocumentFile, folder: String, lrcs: MutableMap<String, Uri>) {
        dir.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            when {
                file.isDirectory -> indexLyrics(file, folder, lrcs)
                isLrc(name) -> putLrc(lrcs, folder, name, file.uri)
            }
        }
    }

    private fun readFile(
        sourceId: String,
        found: FoundAudio,
        lrcs: Map<String, Uri>,
        filters: ScanSettings,
        now: Long,
    ): ScannedFile? {
        val file = found.file
        val name = file.name ?: return null
        if (name.startsWith(".")) return null
        val size = file.length()
        if (filters.rejectsSize(size)) return null
        val uri = file.uri
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrDefault(uri.toString())
        val id = "fld_${sourceId}_${shortHash(docId)}"
        val retriever = MediaMetadataRetriever()
        val meta = try {
            retriever.setDataSource(context, uri)
            Meta(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                picture = retriever.embeddedPicture,
            )
        } catch (_: Exception) {
            Meta(null, null, null, null, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
        val title = displayTitle(meta.title, name)
        val artist = known(meta.artist, "未知艺术家")
        val album = known(meta.album, "未知专辑")
        val mime = file.type ?: mimeOf(name)
        val measured = if (isMp3(name, mime)) mp3Duration.readMillis(uri.toString()) else null
        val durationMs = measured?.takeIf { it >= 500L } ?: meta.duration ?: 0L
        if (filters.rejectsDuration(durationMs)) return null
        val artwork = writeCover(id, meta.picture)
        val lyricUri = findLrc(lrcs, found.folder, name)?.let { lyrics.store(id, it) }
            ?: lyrics.read(uri.toString())?.let { lyrics.storeText(id, it) }
        return ScannedFile(
            TrackEntity(
                id = id,
                sourceId = sourceId,
                remoteId = docId,
                title = title,
                artist = artist,
                artistId = "fldart_${sourceId}_${shortHash(artist.lowercase())}",
                album = album,
                albumId = "fldalb_${sourceId}_${shortHash(artist.lowercase() + "_" + album.lowercase())}",
                durationMs = durationMs,
                trackNumber = trackNumber(meta.track),
                discNumber = null,
                year = yearOf(meta.year),
                mimeType = mime,
                bitrate = null,
                sampleRate = null,
                fileSize = size.takeIf { it > 0L },
                uri = uri.toString(),
                artworkUri = artwork,
                origin = TrackOrigin.LOCAL,
                downloadState = DownloadState.DOWNLOADED,
                isFavorite = false,
                playCount = 0,
                lastPlayedAt = null,
                addedAt = now,
                lyricUri = lyricUri,
                folder = found.folder,
            ),
        )
    }

    private fun writeCover(id: String, picture: ByteArray?): String? {
        if (picture == null || picture.size < 32) return null
        val file = File(context.cacheDir, "covers/$id.jpg")
        file.parentFile?.mkdirs()
        file.writeBytes(picture)
        return Uri.fromFile(file).toString()
    }

    private data class Meta(
        val title: String?,
        val artist: String?,
        val album: String?,
        val duration: Long?,
        val year: String?,
        val track: String?,
        val picture: ByteArray?,
    )

    private data class FoundAudio(val file: DocumentFile, val folder: String)

    private data class ScannedFile(val entity: TrackEntity)

    @Serializable
    private data class FolderConfig(val root: String)

    companion object {
        private const val PREFIX = "fld_"
        private val json = Json { ignoreUnknownKeys = true }
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "ape", "mka", "aiff", "aif")
        private val LYRIC_DIRS = setOf("lrc", "lyric", "lyrics", "歌词")

        private fun isLyricFolder(name: String): Boolean = LYRIC_DIRS.any { name.equals(it, ignoreCase = true) }

        private fun isLrc(name: String): Boolean {
            val lower = name.lowercase()
            return lower.endsWith(".lrc") || lower.endsWith(".lrc.txt")
        }

        private fun joinFolder(parent: String, name: String): String {
            if (parent.isBlank()) return name
            if (name.isBlank()) return parent
            return "$parent/$name"
        }

        private fun putLrc(map: MutableMap<String, Uri>, folder: String, name: String, uri: Uri) {
            val lower = name.lowercase()
            val stem = when {
                lower.endsWith(".lrc.txt") -> lower.removeSuffix(".lrc.txt")
                lower.endsWith(".lrc") -> lower.removeSuffix(".lrc")
                else -> lower.substringBeforeLast('.')
            }
            map[key(folder, stem)] = uri
        }

        private fun findLrc(map: Map<String, Uri>, folder: String, audioName: String): Uri? {
            val lower = audioName.lowercase()
            val stem = lower.substringBeforeLast('.')
            return map[key(folder, stem)] ?: map[key(folder, lower)]
        }

        private fun key(folder: String, name: String): String = "$folder\u0000$name"

        private fun sourceId(uri: Uri): String = PREFIX + shortHash(uri.toString())

        private fun shortHash(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }

        private fun folderName(uri: Uri): String {
            val doc = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull().orEmpty()
            val raw = Uri.decode(doc.substringAfterLast(':').substringAfterLast('/')).trim()
            return raw.ifBlank { "文件夹" }.take(40)
        }

        private fun isAudio(file: DocumentFile): Boolean {
            val name = file.name ?: return false
            if (file.type?.startsWith("audio/") == true) return true
            return name.substringAfterLast('.', "").lowercase() in AUDIO_EXT
        }

        private fun isMp3(name: String, mime: String?): Boolean {
            val type = mime?.lowercase().orEmpty()
            return type == "audio/mpeg" || type == "audio/mp3" || name.endsWith(".mp3", ignoreCase = true)
        }

        private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            else -> "audio/*"
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

        private fun yearOf(raw: String?): Int? = raw?.take(4)?.toIntOrNull()?.takeIf { it > 0 }

        private fun trackNumber(raw: String?): Int? = raw?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    }
}
