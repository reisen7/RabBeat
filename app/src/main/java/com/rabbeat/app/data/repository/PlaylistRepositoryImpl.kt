package com.rabbeat.app.data.repository

import androidx.room.withTransaction
import com.rabbeat.app.data.local.LikedPlaylist
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
) : PlaylistRepository {
    override fun observePlaylists(): Flow<List<UserPlaylist>> =
        combine(playlistDao.observeAll(), playlistDao.observeAllRefs()) { rows, refs ->
            val counts = refs.groupingBy { it.playlistId }.eachCount()
            rows.map { row ->
                UserPlaylist(
                    id = row.id,
                    name = row.name,
                    trackCount = counts[row.id] ?: 0,
                    updatedAt = row.updatedAt,
                    isSystem = row.isSystem,
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

    override suspend fun delete(id: String) {
        if (playlistDao.get(id)?.isSystem == true) return
        playlistDao.deleteTracks(id)
        playlistDao.delete(id)
    }

    override suspend fun deleteMany(ids: List<String>) {
        database.withTransaction {
            ids.forEach { id ->
                if (playlistDao.get(id)?.isSystem == true) return@forEach
                playlistDao.deleteTracks(id)
                playlistDao.delete(id)
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

    override suspend fun addTracks(playlistId: String, trackIds: List<String>, atStart: Boolean) {
        val existing = playlistDao.refs(playlistId)
        val have = existing.map { it.trackId }.toSet()
        val fresh = trackIds.distinct().filter { it !in have }
        if (fresh.isEmpty()) return
        val now = System.currentTimeMillis()
        if (atStart) {
            playlistDao.shift(playlistId, fresh.size)
            playlistDao.upsertTracks(
                fresh.mapIndexed { index, trackId ->
                    PlaylistTrackCrossRef(playlistId, trackId, index, now)
                },
            )
        } else {
            val start = (existing.maxOfOrNull { it.position } ?: -1) + 1
            playlistDao.upsertTracks(
                fresh.mapIndexed { index, trackId ->
                    PlaylistTrackCrossRef(playlistId, trackId, start + index, now)
                },
            )
        }
        playlistDao.touch(playlistId, now)
        if (playlistId == LikedPlaylist.ID) likedPlaylist.onAdded(fresh)
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
        addTracks(id, trackIds, atStart = false)
    }
}
