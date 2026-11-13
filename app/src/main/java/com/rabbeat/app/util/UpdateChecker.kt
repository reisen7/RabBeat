package com.rabbeat.app.util

import com.rabbeat.app.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateResult {
    /** tag_name of the newest release and the page to open. */
    data class Available(val tag: String, val url: String) : UpdateResult
    data object Latest : UpdateResult
    data object Failed : UpdateResult
}

/** Queries GitHub Releases for the latest published version. */
@Singleton
class UpdateChecker @Inject constructor(
    private val logger: AppLogger,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        var lastError = "no attempt"
        for (route in ROUTES) {
            val result = runCatching {
                val request = Request.Builder()
                    .url("${route.api}$API_PATH")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "http ${response.code}"
                        null
                    } else {
                        val obj = json.parseToJsonElement(response.body?.string() ?: "").jsonObject
                        val tag = obj["tag_name"]?.jsonPrimitive?.content
                        val url = obj["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE
                        when {
                            tag == null -> null
                            isNewer(tag, BuildConfig.VERSION_NAME) ->
                                UpdateResult.Available(tag, "${route.web}$url")
                            else -> UpdateResult.Latest
                        }
                    }
                }
            }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
            if (result != null) {
                logger.i("Update", "check result=$result via ${route.api}")
                return@withContext result
            }
            logger.w("Update", "check via ${route.api} failed: $lastError")
        }
        UpdateResult.Failed
    }

    /** True when [tag] (like "v0.2.0") is a higher version than [current]. */
    private fun isNewer(tag: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").removePrefix("V")
            .split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(tag)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    companion object {
        private const val API_PATH = "/repos/reisen7/RabBeat/releases/latest"
        private const val RELEASES_PAGE = "https://github.com/reisen7/RabBeat/releases"

        // Direct GitHub first; public mirrors after for networks where
        // api.github.com is unreachable. Read-only public endpoints, no auth.
        // `web` prefixes the release page URL so the download link works too.
        private data class Route(val api: String, val web: String)

        private val ROUTES = listOf(
            Route("https://api.github.com", ""),
            Route("https://gh-proxy.com/https://api.github.com", "https://gh-proxy.com/"),
            Route("https://ghproxy.net/https://api.github.com", "https://ghproxy.net/"),
        )
    }
}
