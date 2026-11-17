package com.rabbeat.app.desktop.sys
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*

import java.io.File
import java.util.Properties

/** Tiny in-app navigation state — desktop has no back stack framework. */
sealed interface DesktopRoute {
    data object Home : DesktopRoute
    data object Library : DesktopRoute
    data object RecentAdded : DesktopRoute
    data object MostPlayed : DesktopRoute
    data object Playlists : DesktopRoute
    data object Search : DesktopRoute
    data class AlbumDetail(val albumId: String) : DesktopRoute
    data class ArtistDetail(val artistId: String) : DesktopRoute
    data class PlaylistDetail(val playlistId: String) : DesktopRoute
    data class FolderDetail(val path: String) : DesktopRoute
    data object HiddenSongs : DesktopRoute
    data object Settings : DesktopRoute
}

/** Minimal persisted desktop preferences (properties file under ~/.rabbeat). */
class DesktopSettings(private val file: File = File(LibraryScanner.dataDir(), "settings.properties")) {

    var themeMode: String = load("themeMode", "DARK")
        set(value) { field = value; save("themeMode", value) }

    var accent: String = load("accent", "ROSE")
        set(value) { field = value; save("accent", value) }

    var bgImage: String = load("bgImage", "")
        set(value) { field = value; save("bgImage", value) }

    var dimLevel: Float = load("dimLevel", "0.35").toFloatOrNull() ?: 0.35f
        set(value) { field = value; save("dimLevel", value.toString()) }

    var cardOpacity: Float = load("cardOpacity", "0.3").toFloatOrNull() ?: 0.3f
        set(value) { field = value; save("cardOpacity", value.toString()) }

    var bgBlur: Float = load("bgBlur", "22").toFloatOrNull() ?: 22f
        set(value) { field = value; save("bgBlur", value.toString()) }

    var windowW: Int = load("windowW", "1280").toIntOrNull() ?: 1280
        set(value) { field = value; save("windowW", value.toString()) }

    var windowH: Int = load("windowH", "820").toIntOrNull() ?: 820
        set(value) { field = value; save("windowH", value.toString()) }

    var volume: Float = load("volume", "1").toFloatOrNull() ?: 1f
        set(value) { field = value; save("volume", value.toString()) }

    var muted: Boolean = load("muted", "false").toBoolean()
        set(value) { field = value; save("muted", value.toString()) }

    var closeToTray: Boolean = load("closeToTray", "true").toBoolean()
        set(value) { field = value; save("closeToTray", value.toString()) }

    var cacheDir: String = load("cacheDir", "")
        set(value) { field = value; save("cacheDir", value) }

    var convertDir: String = load("convertDir", "")
        set(value) { field = value; save("convertDir", value) }

    fun cacheRoot(): File =
        if (cacheDir.isNotBlank()) File(cacheDir) else File(LibraryScanner.dataDir(), "cache")

    /** Extracted covers, playlist images, and the custom background. Lives inside the cache root. */
    fun artRoot(): File = File(cacheRoot(), "art")

    /** Saved lyrics for tracks that have no sidecar next to the audio file. */
    fun lyricRoot(): File = File(cacheRoot(), "lyrics")

    /** Where decrypted ncm/qmc audio lands. Kept outside [cacheRoot] so cache cleanup never wipes library files. */
    fun convertRoot(): File =
        if (convertDir.isNotBlank()) File(convertDir) else File(LibraryScanner.dataDir(), "music")

    /** Last time the sidebar order changed. Read from disk so every settings instance sees it. */
    fun playlistOrderAt(): Long = load("playlistOrderAt", "0").toLongOrNull() ?: 0L

    fun notePlaylistOrder() {
        save("playlistOrderAt", System.currentTimeMillis().toString())
    }

    private fun load(key: String, fallback: String): String =
        runCatching { Properties().apply { file.inputStream().use(::load) }.getProperty(key) }
            .getOrNull() ?: fallback

    private fun save(key: String, value: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val props = Properties().apply {
                if (file.isFile) file.inputStream().use(::load)
                setProperty(key, value)
            }
            file.outputStream().use { props.store(it, "rabbeat") }
        }
    }
}
