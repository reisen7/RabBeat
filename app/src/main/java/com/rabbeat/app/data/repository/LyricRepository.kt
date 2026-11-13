package com.rabbeat.app.data.repository

import android.net.Uri
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.remote.netease.NeteaseApi
import com.rabbeat.app.data.remote.netease.NeteaseLyrics
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.util.AppLogger
import com.rabbeat.app.util.LrcParser
import com.rabbeat.app.util.LrcLine
import com.rabbeat.app.util.LyricReader
import com.rabbeat.app.util.LyricSheet
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LyricRepository @Inject constructor(
    private val tracks: TrackDao,
    private val reader: LyricReader,
    private val netease: NeteaseApi,
    private val logger: AppLogger,
) {
    /** Tracks whose online lookup already failed this session; don't re-hit the API. */
    private val missed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun load(trackId: String): LyricSheet = withContext(Dispatchers.IO) {
        val fetched = tracks.get(trackId) ?: return@withContext LyricSheet.EMPTY
        val entity = ensureNeteaseLyric(fetched)
        val raw = entity.lyricUri?.let { reader.readText(it) }
            ?: reader.read(entity.uri)
            ?: fetchOnline(entity)
            ?: return@withContext LyricSheet.EMPTY.copy(offsetMs = entity.lyricOffsetMs)
        sheetOf(raw, entity.lyricOffsetMs)
    }

    suspend fun setOffset(trackId: String, offsetMs: Long) = withContext(Dispatchers.IO) {
        tracks.updateLyricOffset(trackId, offsetMs)
    }

    /** Manually (re-)fetch online lyrics; returns true when lyrics were stored. */
    suspend fun fetchOnline(trackId: String): Boolean = withContext(Dispatchers.IO) {
        missed.remove(trackId)
        val entity = tracks.get(trackId) ?: return@withContext false
        fetchOnline(entity) != null
    }

    /** Import a user-picked .lrc/.txt file as this track's lyrics. */
    suspend fun importLocal(trackId: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        val stored = reader.store(trackId, source) ?: return@withContext false
        tracks.updateLyricUri(trackId, stored)
        true
    }

    private suspend fun fetchOnline(entity: TrackEntity): String? {
        if (!missed.add(entity.id)) return null
        val query = "${entity.title} ${entity.artist}".trim()
        if (query.isBlank()) return null
        val candidates = netease.search(query)
        val best = candidates
            .mapNotNull { song ->
                val score = score(entity, song.name, song.artists.map { it.name }, song.duration)
                if (score > 0) song to score else null
            }
            .maxByOrNull { it.second }?.first
        if (best == null) {
            logger.d("Lyric", "no online match for '${entity.title}' by ${entity.artist}")
            return null
        }
        val response = netease.lyrics(best.id) ?: return null
        val merged = NeteaseLyrics.merge(response.lrc?.lyric, response.tlyric?.lyric) ?: return null
        val uri = reader.storeText("match_${entity.id}", merged) ?: return null
        tracks.updateLyricUri(entity.id, uri)
        logger.i("Lyric", "online lyric matched '${entity.title}' -> netease#${best.id}")
        return merged
    }

    /** Positive when the candidate is a plausible match for the track. */
    private fun score(entity: TrackEntity, name: String, artists: List<String>, durationMs: Long): Int {
        val title = normalize(entity.title)
        val candidate = normalize(name)
        var points = when {
            title == candidate -> 4
            title.isNotEmpty() && candidate.isNotEmpty() &&
                (title.contains(candidate) || candidate.contains(title)) -> 2
            else -> return 0
        }
        val localArtists = entity.artist.split('/', '、', ',', ';').map { normalize(it) }
        val remote = artists.map { normalize(it) }
        if (localArtists.any { local -> remote.any { it.isNotEmpty() && (local.contains(it) || it.contains(local)) } }) {
            points += 3
        }
        val diff = abs(durationMs - entity.durationMs)
        if (entity.durationMs > 0 && diff <= 3_000) points += 3 else if (diff <= 8_000) points += 1
        return points
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[(（\\[【].*?[)）\\]】]"), "").trim()

    private fun sheetOf(raw: String, offsetMs: Long): LyricSheet {
        val synced = LrcParser.parse(raw)
        if (synced.isNotEmpty()) return LyricSheet(synced, synced = true, offsetMs = offsetMs)
        val plain = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { LrcLine(0L, it) }.toList()
        return LyricSheet(plain, synced = false, offsetMs = offsetMs)
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
}
