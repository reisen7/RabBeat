package com.rabbeat.app.data.repository

import androidx.room.withTransaction
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.PlaylistCoverStore
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.reorderedPositions
import com.rabbeat.app.data.local.dao.PlaylistDao
import com.rabbeat.app.data.local.dao.SourceDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.domain.repository.PlaylistRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val sourceDao: SourceDao,
    private val likedPlaylist: LikedPlaylist,
    private val database: RabBeatDatabase,
    private val coverStore: PlaylistCoverStore,
) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<UserPlaylist>> =
        combine(
            playlistDao.observeAll(),
            playlistDao.observeVisibleCounts(),
            playlistDao.observeLatestArtwork(),
        ) { rows, counts, artwork ->
            val byPlaylist = counts.associate { it.playlistId to it.trackCount }
            val artworkByPlaylist = artwork.associate { it.playlistId to it.artworkUri }
            rows.map { row ->
                UserPlaylist(
                    id = row.id,
                    name = row.name,
                    trackCount = byPlaylist[row.id] ?: 0,
                    updatedAt = row.updatedAt,
                    isSystem = row.isSystem,
                    coverUri = row.coverUri,
                    autoCoverUri = artworkByPlaylist[row.id],
                )
            }
        }

    override fun observeTracks(playlistId: String): Flow<List<Track>> =
        combine(playlistDao.observeRefs(playlistId), trackDao.observeAll(), sourceDao.observeAll()) { refs, tracks, sources ->
            val byId = tracks.associateBy { it.id }
            val sourcesById = sources.associateBy { it.id }
            refs.mapNotNull { ref ->
                val entity = byId[ref.trackId] ?: return@mapNotNull null
                entity.toDomain(sourcesById[entity.sourceId])
            }
        }

    override suspend fun create(name: String): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        playlistDao.upsert(
            PlaylistEntity(
                id = id,
                name = name.ifBlank { "新建歌单" },
                isSystem = false,
                createdAt = now,
                updatedAt = now,
                sortOrder = (playlistDao.minSortOrder() ?: 0) - 1,
            ),
        )
        return id
    }

    override suspend fun rename(id: String, name: String) {
        if (playlistDao.get(id)?.isSystem == true) return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        playlistDao.rename(id, trimmed, System.currentTimeMillis())
    }

    override suspend fun setCoverUri(id: String, uri: String?) {
        playlistDao.setCover(id, uri)
    }

    override suspend fun delete(id: String) {
        if (playlistDao.get(id)?.isSystem == true) return
        playlistDao.deleteTracks(id)
        playlistDao.delete(id)
        coverStore.release(id)
    }

    override suspend fun deleteMany(ids: List<String>) {
        database.withTransaction {
            ids.forEach { id ->
                if (playlistDao.get(id)?.isSystem == true) return@forEach
                playlistDao.deleteTracks(id)
                playlistDao.delete(id)
                coverStore.release(id)
            }
        }
    }

    override suspend fun reorder(orderedIds: List<String>) {
        database.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                playlistDao.setSortOrder(id, index)
            }
        }
    }

    override suspend fun addTracks(playlistId: String, trackIds: List<String>): Int {
        val existing = playlistDao.refs(playlistId)
        val have = existing.map { it.trackId }.toSet()
        val fresh = trackIds.distinct().filter { it !in have }
        if (fresh.isEmpty()) return 0
        val now = System.currentTimeMillis()
        playlistDao.shift(playlistId, fresh.size)
        playlistDao.upsertTracks(
            fresh.mapIndexed { index, trackId ->
                PlaylistTrackCrossRef(playlistId, trackId, index, now)
            },
        )
        playlistDao.touch(playlistId, now)
        if (playlistId == LikedPlaylist.ID) likedPlaylist.onAdded(fresh)
        return fresh.size
    }

    override suspend fun removeTracks(playlistId: String, trackIds: List<String>) {
        val ids = trackIds.distinct()
        if (ids.isEmpty()) return
        database.withTransaction {
            ids.chunked(400).forEach { playlistDao.removeTracksIn(playlistId, it) }
            playlistDao.touch(playlistId, System.currentTimeMillis())
        }
        if (playlistId == LikedPlaylist.ID) ids.forEach { likedPlaylist.onRemoved(it) }
    }

    override suspend fun reorderTracks(playlistId: String, orderedTrackIds: List<String>) {
        database.withTransaction {
            val existing = playlistDao.refs(playlistId).map { it.trackId }
            reorderedPositions(existing, orderedTrackIds).forEachIndexed { index, trackId ->
                playlistDao.setTrackPosition(playlistId, trackId, index)
            }
            playlistDao.touch(playlistId, System.currentTimeMillis())
        }
    }

    override suspend fun removeTrack(playlistId: String, trackId: String) {
        playlistDao.removeTrack(playlistId, trackId)
        playlistDao.touch(playlistId, System.currentTimeMillis())
        if (playlistId == LikedPlaylist.ID) likedPlaylist.onRemoved(trackId)
    }

    override suspend fun saveQueue(name: String, trackIds: List<String>) {
        val id = create(name.ifBlank { "我的队列" })
        addTracks(id, trackIds)
    }
}
