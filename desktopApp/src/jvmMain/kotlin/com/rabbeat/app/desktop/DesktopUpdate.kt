package com.rabbeat.app.desktop

import java.awt.Desktop
import java.awt.SystemTray
import java.awt.TrayIcon
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** GitHub Releases check — same API as the Android UpdateChecker. */
object DesktopUpdate {
    const val CURRENT = "0.1.0"
    private const val API = "https://api.github.com/repos/reisen7/RabBeat/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }

    data class Result(val tag: String, val notes: String, val pageUrl: String, val assetUrl: String?)

    suspend fun check(): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val body = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI(API)).header("Accept", "application/vnd.github+json").GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            ).takeIf { it.statusCode() == 200 }?.body() ?: return@withContext null
            val rel = json.parseToJsonElement(body).jsonObject
            val tag = rel["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            val newer = tag.removePrefix("v") != CURRENT
            if (!newer) return@withContext null
            Result(
                tag = tag,
                notes = rel["body"]?.jsonPrimitive?.content.orEmpty(),
                pageUrl = rel["html_url"]?.jsonPrimitive?.content.orEmpty(),
                assetUrl = rel["assets"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["browser_download_url"]?.jsonPrimitive?.content }
                    ?.firstOrNull { it.endsWith(".msi") || it.endsWith(".exe") || it.endsWith(".zip") },
            )
        }.getOrNull()
    }

    fun browse(url: String) = runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}

/** Balloon notification via the AWT tray icon created by the Compose Tray. */
object DesktopNotify {
    fun trackChanged(title: String, artist: String) = runCatching {
        if (!SystemTray.isSupported()) return@runCatching
        SystemTray.getSystemTray().trayIcons.firstOrNull()
            ?.displayMessage(title, artist, TrayIcon.MessageType.INFO)
    }
}
