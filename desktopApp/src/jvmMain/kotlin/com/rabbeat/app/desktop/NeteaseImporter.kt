package com.rabbeat.app.desktop

import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.TrackDeduper
import com.rabbeat.app.data.local.toCandidate
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.data.remote.netease.NeteaseLinks
import com.rabbeat.app.data.remote.netease.NeteaseMapper
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NeteaseImportResult(val playlistName: String, val imported: Int, val matched: Int, val skipped: Int)

/** Desktop port of the Android Netease playlist importer — same entity semantics. */
class NeteaseImporter(private val db: RabBeatDatabase) {

    suspend fun import(input: String, onProgress: (done: Int, total: Int) -> Unit): NeteaseImportResult =
        withContext(Dispatchers.IO) {
            var playlistId = NeteaseLinks.parsePlaylistId(input)
            if (playlistId == null) {
                playlistId = NeteaseLinks.findShortLink(input)
                    ?.let { NeteaseClient.resolveRedirect(it) }
                    ?.let { NeteaseLinks.parsePlaylistId(it) }
            }
            requireNotNull(playlistId) { "无法解析歌单链接" }

            val detail = NeteaseClient.playlist(playlistId) ?: error("歌单获取失败，检查网络")
            val wanted = detail.trackIds.map { it.id }.distinct()
            val now = System.currentTimeMillis()

            db.sourceDao().upsert(
                SourceEntity(
                    id = NETEASE_SOURCE_ID,
                    type = SourceType.NETEASE,
                    displayName = "网易云音乐",
                    configJson = "{}",
                    status = SourceStatus.CONNECTED,
                    lastSyncAt = now,
                    createdAt = db.sourceDao().get(NETEASE_SOURCE_ID)?.createdAt ?: now,
                ),
            )

            // Local candidates for dedup (non-netease rows).
            val locals = db.trackDao()
                .query(androidx.room.RoomRawQuery("SELECT * FROM tracks WHERE sourceId != '$NETEASE_SOURCE_ID'"))
                .map { it.toCandidate() }
            val existing = db.trackDao().getBySource(NETEASE_SOURCE_ID).associateBy { it.remoteId }
            var imported = 0
            var matched = 0
            var skipped = 0
            val upserts = mutableListOf<TrackEntity>()
            val refs = mutableListOf<String>()

            wanted.forEachIndexed { index, neId ->
                onProgress(index, wanted.size)
                val detailChunk = NeteaseClient.songDetails(listOf(neId)) ?: run { skipped++; return@forEachIndexed }
                val song = detailChunk.songs.firstOrNull { it.id == neId } ?: run { skipped++; return@forEachIndexed }
                val privilege = detailChunk.privileges.firstOrNull { it.id == neId }

                val local = locals.firstOrNull { TrackDeduper.isDuplicate(it, NeteaseMapper.toCandidate(song)) }
                if (local != null) {
                    matched++
                    refs += local.id
                    return@forEachIndexed
                }
                if (!NeteaseMapper.isPlayable(privilege)) {
                    skipped++
                    return@forEachIndexed
                }
                val entity = NeteaseMapper.toTrackEntity(song, now)
                upserts += existing[entity.remoteId]?.let { p ->
                    entity.copy(
                        isFavorite = p.isFavorite, playCount = p.playCount,
                        lastPlayedAt = p.lastPlayedAt, addedAt = p.addedAt,
                        downloadState = p.downloadState, lyricUri = p.lyricUri, hidden = p.hidden,
                    )
                } ?: entity
                refs += entity.id
                imported++
            }
            onProgress(wanted.size, wanted.size)

            upserts.chunked(200).forEach { db.trackDao().upsertAll(it) }
            rebuildNeteaseCatalog()

            val playlistTableId = "ne_pl_$playlistId"
            val old = db.playlistDao().get(playlistTableId)
            val taken = db.playlistDao().getAll().filter { it.id != playlistTableId }.map { it.name }.toSet()
            val finalName = old?.name ?: uniqueName(detail.name.ifBlank { "网易云歌单" }, taken)
            db.playlistDao().upsert(
                PlaylistEntity(
                    id = playlistTableId,
                    name = finalName,
                    isSystem = false,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now,
                    sortOrder = old?.sortOrder ?: ((db.playlistDao().minSortOrder() ?: 0) - 1),
                    coverUri = old?.coverUri ?: detail.coverImgUrl,
                ),
            )
            db.playlistDao().deleteTracks(playlistTableId)
            db.playlistDao().upsertTracks(
                refs.distinct().mapIndexed { i, trackId -> PlaylistTrackCrossRef(playlistTableId, trackId, i, now) },
            )
            DesktopLog.i("Netease", "import '$finalName': imported=$imported matched=$matched skipped=$skipped")
            NeteaseImportResult(finalName, imported, matched, skipped)
        }

    private suspend fun rebuildNeteaseCatalog() {
        val tracks = db.trackDao().getBySource(NETEASE_SOURCE_ID)
        db.albumDao().deleteBySource(NETEASE_SOURCE_ID)
        db.artistDao().deleteBySource(NETEASE_SOURCE_ID)
        val albums = tracks.groupBy { it.albumId }.map { (id, songs) ->
            val first = songs.first()
            com.rabbeat.app.data.local.entity.AlbumEntity(
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
            com.rabbeat.app.data.local.entity.ArtistEntity(
                id = id ?: "neart_unknown",
                sourceId = NETEASE_SOURCE_ID,
                name = first.artist,
                artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
                albumCount = songs.map { it.albumId }.distinct().size,
                trackCount = songs.size,
            )
        }
        albums.chunked(200).forEach { db.albumDao().upsertAll(it) }
        artists.chunked(200).forEach { db.artistDao().upsertAll(it) }
    }

    private fun uniqueName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        if ("$base new" !in taken) return "$base new"
        return generateSequence(2) { it + 1 }.map { "$base new$it" }.first { it !in taken }
    }
}
