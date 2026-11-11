package com.rabbeat.app.playback

import androidx.media3.datasource.cache.ContentMetadata
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.domain.model.DownloadState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class NeteaseCacheStatus @Inject constructor(
    private val trackDao: TrackDao,
    private val mediaCache: MediaCache,
) {
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val tracks = trackDao.getBySource(NETEASE_SOURCE_ID)
        if (tracks.isEmpty()) return@withContext
        val cache = mediaCache.cache
        val cached = mutableListOf<String>()
        val gone = mutableListOf<String>()
        tracks.forEach { track ->
            val key = cacheKey(track.remoteId) ?: return@forEach
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            val full = length > 0L && cache.isCached(key, 0L, length)
            when {
                full && track.downloadState != DownloadState.DOWNLOADED -> cached += track.id
                !full && track.downloadState == DownloadState.DOWNLOADED -> gone += track.id
            }
        }
        if (cached.isNotEmpty()) trackDao.updateDownloadStates(cached, DownloadState.DOWNLOADED)
        if (gone.isNotEmpty()) trackDao.updateDownloadStates(gone, DownloadState.NONE)
    }

    companion object {
        fun cacheKey(remoteId: String?): String? = remoteId?.let { "netease:$it" }
    }
}
