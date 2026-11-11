package com.rabbeat.app.data.local

import androidx.room.withTransaction
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class NeteaseReconciler @Inject constructor(
    private val database: RabBeatDatabase,
) {
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        database.withTransaction {
            val trackDao = database.trackDao()
            val netease = trackDao.getBySource(NETEASE_SOURCE_ID)
            if (netease.isEmpty()) return@withTransaction
            val locals = trackDao.getOtherSources(NETEASE_SOURCE_ID)
            val candidates = locals.map { it.toCandidate() }
            val dead = mutableListOf<String>()
            netease.forEach { remote ->
                val candidate = remote.toCandidate()
                val winnerIndex = candidates.indexOfFirst { TrackDeduper.isDuplicate(it, candidate) }
                if (winnerIndex < 0) return@forEach
                val winner = locals[winnerIndex]
                trackDao.reassignPlaylistTracks(remote.id, winner.id)
                trackDao.removePlaylistTracks(remote.id)
                trackDao.reassignPlayHistory(remote.id, winner.id)
                trackDao.reassignLyricLinks(remote.id, winner.id)
                trackDao.removeLyricLink(remote.id)
                trackDao.reassignPlaybackState(remote.id, winner.id)
                trackDao.mergeStatsInto(
                    winner.id,
                    remote.isFavorite,
                    remote.playCount,
                    remote.lastPlayedAt,
                    remote.addedAt,
                )
                dead += remote.id
            }
            if (dead.isNotEmpty()) {
                trackDao.deleteByIds(dead)
                rebuildNeteaseCatalog(database)
            }
        }
    }
}
