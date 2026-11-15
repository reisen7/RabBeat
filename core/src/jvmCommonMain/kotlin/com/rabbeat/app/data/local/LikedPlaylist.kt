package com.rabbeat.app.data.local

import com.rabbeat.app.data.local.dao.PlaylistDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LikedPlaylist @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
) {
    suspend fun ensure() {
        val now = System.currentTimeMillis()
        if (playlistDao.get(ID) == null) {
            playlistDao.upsert(PlaylistEntity(ID, NAME, isSystem = true, createdAt = now, updatedAt = now))
        }
        val member = playlistDao.refs(ID).map { it.trackId }.toSet()
        val favorites = trackDao.favoriteIds().toSet()
        val missing = favorites.filter { it !in member }
        if (missing.isNotEmpty()) {
            playlistDao.shift(ID, missing.size)
            playlistDao.upsertTracks(
                missing.mapIndexed { index, trackId ->
                    PlaylistTrackCrossRef(ID, trackId, index, now)
                },
            )
            playlistDao.touch(ID, now)
        }
        val unfavorited = member.filter { it !in favorites }
        if (unfavorited.isNotEmpty()) {
            unfavorited.chunked(400).forEach { trackDao.markFavorites(it) }
        }
    }

    suspend fun toggle(trackId: String) {
        val now = System.currentTimeMillis()
        if (playlistDao.get(ID) == null) {
            playlistDao.upsert(PlaylistEntity(ID, NAME, isSystem = true, createdAt = now, updatedAt = now))
        }
        val member = playlistDao.refs(ID).any { it.trackId == trackId }
        if (member) {
            playlistDao.removeTrack(ID, trackId)
            trackDao.setFavorite(trackId, false)
        } else {
            playlistDao.shift(ID, 1)
            playlistDao.upsertTracks(listOf(PlaylistTrackCrossRef(ID, trackId, 0, now)))
            trackDao.setFavorite(trackId, true)
        }
        playlistDao.touch(ID, now)
    }

    suspend fun onAdded(ids: List<String>) {
        ids.chunked(400).forEach { trackDao.markFavorites(it) }
    }

    suspend fun onRemoved(id: String) {
        trackDao.setFavorite(id, false)
    }

    companion object {
        const val ID = "system_liked"
        const val NAME = "我喜欢"
    }
}
