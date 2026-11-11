package com.rabbeat.app.data.local

import androidx.room.withTransaction
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.remote.netease.NeteaseApi
import com.rabbeat.app.data.remote.netease.NeteaseLinks
import com.rabbeat.app.data.remote.netease.NeteaseMapper
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.data.remote.netease.NePrivilege
import com.rabbeat.app.data.remote.netease.NeSong
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NeteaseImportResult(
    val playlistName: String,
    val imported: Int,
    val matchedLocal: Int,
    val skipped: Int,
)

class InvalidNeteaseLinkException : Exception()
class NeteaseNetworkException : Exception()

@Singleton
class NeteasePlaylistImporter @Inject constructor(
    private val database: RabBeatDatabase,
    private val api: NeteaseApi,
    private val reconciler: NeteaseReconciler,
) {
    suspend fun import(
        input: String,
        onProgress: (done: Int, total: Int) -> Unit,
    ): NeteaseImportResult = withContext(Dispatchers.IO) {
        var playlistId = NeteaseLinks.parsePlaylistId(input)
        if (playlistId == null) {
            playlistId = NeteaseLinks.findShortLink(input)
                ?.let { api.resolveRedirect(it) }
                ?.let(NeteaseLinks::parsePlaylistId)
        }
        if (playlistId == null) throw InvalidNeteaseLinkException()

        val detail = api.playlist(playlistId) ?: throw NeteaseNetworkException()
        val wanted = detail.trackIds.map { it.id }.distinct()
        onProgress(0, wanted.size)

        val songs = mutableListOf<NeSong>()
        val privileges = mutableListOf<NePrivilege>()
        wanted.chunked(100).forEachIndexed { index, batch ->
            val response = api.songDetails(batch) ?: throw NeteaseNetworkException()
            songs += response.songs
            privileges += response.privileges
            onProgress(((index + 1) * 100).coerceAtMost(wanted.size), wanted.size)
        }

        val byId = songs.associateBy { it.id }
        val playable = privileges.associate { it.id to NeteaseMapper.isPlayable(it) }
        var imported = 0
        var matched = 0
        var skipped = 0
        val now = System.currentTimeMillis()

        database.withTransaction {
            val trackDao = database.trackDao()
            val sourceDao = database.sourceDao()
            val previous = sourceDao.get(NETEASE_SOURCE_ID)
            sourceDao.upsert(
                SourceEntity(
                    id = NETEASE_SOURCE_ID,
                    type = SourceType.NETEASE,
                    displayName = "网易云音乐",
                    configJson = "{}",
                    status = SourceStatus.CONNECTED,
                    lastSyncAt = now,
                    createdAt = previous?.createdAt ?: now,
                ),
            )
            val locals = trackDao.getOtherSources(NETEASE_SOURCE_ID).map { it.toCandidate() }
            val existing = trackDao.getBySource(NETEASE_SOURCE_ID).associateBy { it.remoteId }

            val upserts = mutableListOf<TrackEntity>()
            val refs = mutableListOf<String>()
            wanted.forEach { neId ->
                val song = byId[neId]
                if (song == null) {
                    skipped++
                    return@forEach
                }
                val local = locals.firstOrNull { TrackDeduper.isDuplicate(it, NeteaseMapper.toCandidate(song)) }
                if (local != null) {
                    matched++
                    refs += local.id
                    return@forEach
                }
                if (playable[neId] != true) {
                    skipped++
                    return@forEach
                }
                val entity = NeteaseMapper.toTrackEntity(song, now)
                val preserved = existing[entity.remoteId]
                upserts += if (preserved != null) {
                    entity.copy(
                        isFavorite = preserved.isFavorite,
                        playCount = preserved.playCount,
                        lastPlayedAt = preserved.lastPlayedAt,
                        addedAt = preserved.addedAt,
                        downloadState = preserved.downloadState,
                        lyricUri = preserved.lyricUri,
                    )
                } else {
                    entity
                }
                refs += entity.id
                imported++
            }
            upserts.chunked(200).forEach { trackDao.upsertAll(it) }
            rebuildNeteaseCatalog(database)

            val playlistDao = database.playlistDao()
            val playlistTableId = "ne_pl_$playlistId"
            val old = playlistDao.get(playlistTableId)
            playlistDao.upsert(
                PlaylistEntity(
                    id = playlistTableId,
                    name = detail.name.ifBlank { "网易云歌单" },
                    isSystem = false,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            playlistDao.deleteTracks(playlistTableId)
            playlistDao.upsertTracks(
                refs.distinct().mapIndexed { index, trackId ->
                    PlaylistTrackCrossRef(playlistTableId, trackId, index, now)
                },
            )
        }
        reconciler.reconcile()
        NeteaseImportResult(detail.name, imported, matched, skipped)
    }
}

internal suspend fun rebuildNeteaseCatalog(database: RabBeatDatabase) {
    val tracks = database.trackDao().getBySource(NETEASE_SOURCE_ID)
    database.albumDao().deleteBySource(NETEASE_SOURCE_ID)
    database.artistDao().deleteBySource(NETEASE_SOURCE_ID)
    val albums = tracks.groupBy { it.albumId }.map { (id, songs) ->
        val first = songs.first()
        AlbumEntity(
            id = id ?: "nealb_unknown",
            sourceId = NETEASE_SOURCE_ID,
            title = first.album,
            artist = first.artist,
            year = songs.mapNotNull { it.year }.maxOrNull(),
            artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
            trackCount = songs.size,
            addedAt = songs.minOf { it.addedAt },
        )
    }
    val artists = tracks.groupBy { it.artistId }.map { (id, songs) ->
        val first = songs.first()
        ArtistEntity(
            id = id ?: "neart_unknown",
            sourceId = NETEASE_SOURCE_ID,
            name = first.artist,
            artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
            albumCount = songs.map { it.albumId }.distinct().size,
            trackCount = songs.size,
        )
    }
    albums.chunked(200).forEach { database.albumDao().upsertAll(it) }
    artists.chunked(200).forEach { database.artistDao().upsertAll(it) }
}
