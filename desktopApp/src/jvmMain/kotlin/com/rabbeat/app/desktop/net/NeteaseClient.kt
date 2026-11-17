package com.rabbeat.app.desktop.net
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.sys.*

import com.rabbeat.app.data.remote.netease.NeLyricResponse
import com.rabbeat.app.data.remote.netease.NePlaylist
import com.rabbeat.app.data.remote.netease.NePlaylistResponse
import com.rabbeat.app.data.remote.netease.NeSongDetailResponse
import com.rabbeat.app.data.remote.netease.NETEASE_SOURCE_ID
import com.rabbeat.app.data.remote.netease.NeteaseLyrics
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Desktop NetEase client — same endpoints/shape as the Android OkHttp implementation. */
object NeteaseClient {
    private const val HOST = "https://music.163.com"
    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()
    private val noRedirect: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    suspend fun playlist(id: Long): NePlaylist? = withContext(Dispatchers.IO) {
        get("$HOST/api/v6/playlist/detail?id=$id")
            ?.let { runCatching { json.decodeFromString(NePlaylistResponse.serializer(), it).playlist }.getOrNull() }
    }

    suspend fun songDetails(ids: List<Long>): NeSongDetailResponse? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(
            ids.joinToString(prefix = "[", postfix = "]") { """{"id":$it}""" },
            Charsets.UTF_8,
        )
        get("$HOST/api/v3/song/detail?c=$encoded")
            ?.let { runCatching { json.decodeFromString(NeSongDetailResponse.serializer(), it) }.getOrNull() }
    }

    suspend fun lyric(id: Long): NeLyricResponse? = withContext(Dispatchers.IO) {
        get("$HOST/api/song/lyric?id=$id&lv=1&tv=-1")
            ?.let { runCatching { json.decodeFromString(NeLyricResponse.serializer(), it) }.getOrNull() }
    }

    suspend fun resolveRedirect(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = noRedirect.send(
                HttpRequest.newBuilder(URI(url)).header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "$HOST/").GET().build(),
                HttpResponse.BodyHandlers.discarding(),
            )
            if (resp.statusCode() in 300..399) resp.headers().firstValue("Location").orElse(null) else null
        }.getOrNull()
    }

    /** Known NetEase song id on an imported row, otherwise null. */
    fun songIdOf(trackId: String, sourceId: String, remoteId: String?): Long? =
        remoteId?.toLongOrNull()?.takeIf { sourceId == NETEASE_SOURCE_ID || trackId.startsWith("ne_") }

    /**
     * Lyrics for a track. Imported NetEase songs use their own song id;
     * everything else is a title/artist search. Returns merged .lrc text, or null.
     */
    suspend fun fetchLrc(title: String, artist: String, songId: Long? = null): String? = withContext(Dispatchers.IO) {
        if (songId != null) merged(songId)?.let { return@withContext it }
        val found = searchSongId("$title $artist") ?: searchSongId(title) ?: return@withContext null
        merged(found)
    }

    private suspend fun merged(songId: Long): String? =
        lyric(songId)?.let { NeteaseLyrics.merge(it.lrc?.lyric, it.tlyric?.lyric) }

    private suspend fun searchSongId(keyword: String): Long? {
        val q = URLEncoder.encode(keyword, Charsets.UTF_8)
        val body = get("$HOST/api/search/get/web?s=$q&type=1&limit=5") ?: return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["result"]
                ?.jsonObject?.get("songs")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        val req = HttpRequest.newBuilder(URI(url))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Referer", "$HOST/")
            .GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 200) resp.body() else null
    }.getOrNull()
}
