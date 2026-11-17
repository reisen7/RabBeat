package com.rabbeat.app.desktop.sys
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*

import java.awt.Desktop
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub Releases check. A failed request is not "already up to date". */
object DesktopUpdate {
    val CURRENT: String get() = System.getProperty("rabbeat.version") ?: "0.0.0-dev"

    private const val API_PATH = "/repos/reisen7/RabBeat/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/reisen7/RabBeat/releases"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    data class Available(val tag: String, val notes: String, val pageUrl: String, val assetUrl: String?)

    sealed interface Check {
        data class Update(val release: Available) : Check
        data object Latest : Check
        data object Failed : Check
    }

    /** Direct GitHub first, then public mirrors when api.github.com is blocked or rate-limited. */
    private data class Route(val api: String, val web: String)

    private val routes = listOf(
        Route("https://api.github.com", ""),
        Route("https://gh-proxy.com/https://api.github.com", "https://gh-proxy.com/"),
        Route("https://ghproxy.net/https://api.github.com", "https://ghproxy.net/"),
    )

    suspend fun check(): Check = withContext(Dispatchers.IO) {
        var lastError = "no attempt"
        for (route in routes) {
            val result = runCatching { fetch(route) }.onFailure {
                lastError = it.message ?: it.javaClass.simpleName
            }.getOrNull()
            if (result != null) {
                DesktopLog.i("Update", "check result=$result via ${route.api}")
                return@withContext result
            }
            DesktopLog.w("Update", "check via ${route.api} failed: $lastError")
        }
        Check.Failed
    }

    private fun fetch(route: Route): Check? {
        val response = http.send(
            HttpRequest.newBuilder(URI("${route.api}$API_PATH"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RabBeat")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != 200) {
            error("http ${response.statusCode()}")
        }
        val rel = json.parseToJsonElement(response.body()).jsonObject
        val tag = rel["tag_name"]?.jsonPrimitive?.content ?: return null
        if (!isNewerRelease(tag, CURRENT)) return Check.Latest
        val page = rel["html_url"]?.jsonPrimitive?.content ?: RELEASES_PAGE
        val assets = rel["assets"]?.jsonArray.orEmpty().mapNotNull {
            it.jsonObject["browser_download_url"]?.jsonPrimitive?.content
        }
        return Check.Update(
            Available(
                tag = tag,
                notes = rel["body"]?.jsonPrimitive?.content.orEmpty(),
                pageUrl = route.web + page,
                assetUrl = pickWindowsAsset(assets)?.let { route.web + it },
            ),
        )
    }

    fun browse(url: String) = runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}

/** True when [tag] (like "v0.0.5") is a higher version than [current]. */
internal fun isNewerRelease(tag: String, current: String): Boolean {
    fun parts(v: String) = v.removePrefix("v").removePrefix("V")
        .substringBefore('+')
        .split('.', '-')
        .mapNotNull { it.toIntOrNull() }
    val remote = parts(tag)
    val local = parts(current)
    if (remote.isEmpty()) return false
    for (i in 0 until maxOf(remote.size, local.size)) {
        val x = remote.getOrElse(i) { 0 }
        val y = local.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

internal fun pickWindowsAsset(urls: List<String>): String? =
    urls.firstOrNull { it.endsWith("-windows-setup.exe", ignoreCase = true) }
        ?: urls.firstOrNull { it.endsWith("-windows-portable.zip", ignoreCase = true) }
        ?: urls.firstOrNull {
            val name = it.substringAfterLast('/').lowercase()
            (name.endsWith(".exe") || name.endsWith(".msi") || name.endsWith(".zip")) &&
                !name.endsWith(".sha256")
        }
