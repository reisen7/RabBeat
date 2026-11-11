package com.rabbeat.app.data.repository

import com.rabbeat.app.data.local.dao.LyricLinkDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.LyricLinkEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.remote.netease.NeteaseApi
import com.rabbeat.app.data.remote.netease.NeteaseLyrics
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.util.LrcParser
import com.rabbeat.app.util.LrcLine
import com.rabbeat.app.util.LyricReader
import com.rabbeat.app.util.LyricSheet
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LyricRepository @Inject constructor(
    private val links: LyricLinkDao,
    private val tracks: TrackDao,
    private val reader: LyricReader,
    private val netease: NeteaseApi,
) {
    suspend fun load(trackId: String): LyricSheet = withContext(Dispatchers.IO) {
        val linked = links.get(trackId) != null
        val targetId = resolve(trackId)
        val fetched = tracks.get(targetId) ?: return@withContext LyricSheet.EMPTY.copy(linked = linked)
        val entity = ensureNeteaseLyric(fetched)
        val raw = entity.lyricUri?.let { reader.readText(it) } ?: reader.read(entity.uri)
            ?: return@withContext LyricSheet.EMPTY.copy(linked = linked)
        val synced = LrcParser.parse(raw)
        if (synced.isNotEmpty()) {
            LyricSheet(synced, synced = true, linked = linked)
        } else {
            val plain = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { LrcLine(0L, it) }.toList()
            LyricSheet(plain, synced = false, linked = linked)
        }
    }

    suspend fun link(trackId: String, sourceTrackId: String) {
        if (trackId == sourceTrackId) {
            links.delete(trackId)
            return
        }
        links.upsert(LyricLinkEntity(trackId, sourceTrackId))
    }

    suspend fun unlink(trackId: String) {
        links.delete(trackId)
    }

    private suspend fun ensureNeteaseLyric(entity: TrackEntity): TrackEntity {
        if (entity.sourceId != NETEASE_SOURCE_ID || entity.lyricUri != null) return entity
        val remoteId = entity.remoteId?.toLongOrNull() ?: return entity
        return runCatching {
            val response = netease.lyrics(remoteId) ?: return entity
            val merged = NeteaseLyrics.merge(response.lrc?.lyric, response.tlyric?.lyric) ?: return entity
            val uri = reader.storeText("netease_$remoteId", merged) ?: return entity
            tracks.updateLyricUri(entity.id, uri)
            entity.copy(lyricUri = uri)
        }.getOrDefault(entity)
    }

    private suspend fun resolve(start: String): String {
        var current = start
        val seen = mutableSetOf<String>()
        repeat(8) {
            if (!seen.add(current)) return start
            current = links.get(current)?.sourceTrackId ?: return current
        }
        return current
    }
}
