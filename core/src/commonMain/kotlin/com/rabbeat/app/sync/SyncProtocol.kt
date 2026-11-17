package com.rabbeat.app.sync

import com.rabbeat.app.data.local.entity.TrackEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val SyncJson = Json { ignoreUnknownKeys = true }

@Serializable
data class PairRequest(val code: String)

@Serializable
data class PairResponse(val token: String)

@Serializable
data class SyncError(val error: String)

@Serializable
data class SyncTrack(
    val syncId: String,
    val contentKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val size: Long,
    val ext: String,
    val hasFile: Boolean,
    val favorite: Boolean = false,
    val hasArt: Boolean = false,
    val hasLyric: Boolean = false,
    val lyricOffsetMs: Long = 0,
    val artUrl: String? = null,
    /** Other ids for this same song, such as a file hash kept beside a NetEase id. */
    val aliasKeys: List<String> = emptyList(),
)

@Serializable
data class SyncPlaylist(
    val syncId: String,
    val name: String,
    val updatedAt: Long,
    val sortOrder: Int,
    val trackSyncIds: List<String>,
    val hasCover: Boolean = false,
    val coverUrl: String? = null,
)

@Serializable
data class SyncHello(
    val deviceName: String,
    val tracks: List<SyncTrack>,
    val playlists: List<SyncPlaylist>,
    val likes: List<String>,
    /** When this device last changed 我喜欢. The newer list replaces the other, including removals. */
    val likesUpdatedAt: Long = 0,
    /** When this device last changed the sidebar order. Song edits do not move it. */
    val playlistOrderAt: Long = 0,
)

@Serializable
data class SyncAdopt(val contentKey: String, val syncId: String)

@Serializable
data class SyncArtLink(val syncId: String, val url: String)

/** Remote cover address. Local cover files are transferred separately. */
fun coverUrl(raw: String?): String? {
    val url = raw?.trim() ?: return null
    if (url.length !in 12..2000) return null
    if (url.any { it.isWhitespace() }) return null
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return url
}

@Serializable
data class SyncPlan(
    val pull: List<SyncTrack>,
    val push: List<SyncTrack>,
    val playlists: List<SyncPlaylist>,
    val likes: List<String>,
    val adopt: List<SyncAdopt> = emptyList(),
    val artPull: List<String> = emptyList(),
    val artPush: List<String> = emptyList(),
    val lyricPull: List<String> = emptyList(),
    val lyricPush: List<String> = emptyList(),
    val artLinks: List<SyncArtLink> = emptyList(),
    val coverPull: List<String> = emptyList(),
    val coverPush: List<String> = emptyList(),
    /** Sidebar order to keep. The newer [SyncHello.playlistOrderAt] wins; a tie keeps the computer. */
    val playlistOrderAt: Long = 0,
    /** 我喜欢 list to keep. The newer [SyncHello.likesUpdatedAt] wins; a tie keeps the computer. */
    val likesUpdatedAt: Long = 0,
)

fun TrackEntity.syncNeteaseId(): String? {
    if (!id.startsWith("ne_")) return null
    val songId = id.removePrefix("ne_")
    if (songId.isEmpty() || songId.any { !it.isDigit() }) return null
    return songId
}

/**
 * The id sent to the other device, plus any extra ids that still mean this song.
 * A NetEase song keeps a previously stored file hash in the database so the next
 * sync still recognizes a device that knows the song by that hash.
 */
data class SyncKeys(val contentKey: String, val aliasKeys: List<String>, val remember: String)

fun syncKeys(neteaseKey: String?, storedKey: String?, fileKey: String?): SyncKeys? {
    val stored = storedKey?.takeIf { it.isNotBlank() }
    val file = fileKey?.takeIf { it.isNotBlank() }
    val netease = neteaseKey?.takeIf { it.isNotBlank() }
    val contentKey = netease ?: stored ?: file ?: return null
    val alias = ArrayList<String>(2)
    if (stored != null && stored != contentKey) alias.add(stored)
    if (file != null && file != contentKey && file !in alias) alias.add(file)
    val remember = if (stored != null && netease != null && stored != netease) stored else contentKey
    return SyncKeys(contentKey, alias, remember)
}

fun audioExt(fileName: String?, mime: String?): String {
    val raw = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
    val ext = raw.filter { it.isLetterOrDigit() }
    val named = fileName?.contains('.') == true && ext.isNotBlank() && ext != "audio" && ext.length <= 5
    if (named) return ext
    val kind = mime?.lowercase().orEmpty()
    return when {
        "flac" in kind -> "flac"
        "ogg" in kind || "opus" in kind -> "ogg"
        "wav" in kind -> "wav"
        "aac" in kind || "mp4" in kind || "m4a" in kind -> "m4a"
        else -> "mp3"
    }
}

fun syncFileName(track: SyncTrack): String {
    val ext = track.ext.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp3" }.take(8)
    val base = "${track.title} - ${track.artist}"
        .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), " ")
        .trim()
        .trimEnd('.', ' ')
        .take(60)
        .ifBlank { "track" }
    return "$base-${track.syncId.take(6)}.$ext"
}
