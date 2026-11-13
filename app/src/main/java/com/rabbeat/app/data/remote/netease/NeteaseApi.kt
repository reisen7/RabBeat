package com.rabbeat.app.data.remote.netease

import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class NeteaseApi @Inject constructor() {
    private val client = OkHttpClient()
    private val noRedirect = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun playlist(id: Long): NePlaylist? = withContext(Dispatchers.IO) {
        val body = get("$HOST/api/v6/playlist/detail?id=$id") ?: return@withContext null
        runCatching { json.decodeFromString(NePlaylistResponse.serializer(), body).playlist }.getOrNull()
    }

    suspend fun songDetails(ids: List<Long>): NeSongDetailResponse? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(
            ids.joinToString(prefix = "[", postfix = "]") { """{"id":$it}""" },
            "UTF-8",
        )
        val body = get("$HOST/api/v3/song/detail?c=$encoded") ?: return@withContext null
        runCatching { json.decodeFromString(NeSongDetailResponse.serializer(), body) }.getOrNull()
    }

    suspend fun search(keyword: String, limit: Int = 8): List<NeSearchSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val body = get("$HOST/api/search/get?s=$encoded&type=1&limit=$limit") ?: return@withContext emptyList()
        runCatching {
            json.decodeFromString(NeSearchResponse.serializer(), body).result?.songs
        }.getOrNull().orEmpty()
    }

    suspend fun lyrics(id: Long): NeLyricResponse? = withContext(Dispatchers.IO) {
        val body = get("$HOST/api/song/lyric?id=$id&lv=1&tv=-1") ?: return@withContext null
        runCatching { json.decodeFromString(NeLyricResponse.serializer(), body) }.getOrNull()
    }

    suspend fun resolveRedirect(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        runCatching {
            noRedirect.newCall(request).execute().use { response ->
                if (response.code in 300..399) response.header("Location") else null
            }
        }.getOrNull()
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    companion object {
        private const val HOST = "https://music.163.com"
        private const val USER_AGENT = "Mozilla/5.0"
        private const val REFERER = "https://music.163.com/"
    }
}
