package com.rabbeat.app.data.local

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.remote.netease.NeteaseMapper
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.TrackOrigin
import com.rabbeat.app.sync.ContentKey
import com.rabbeat.app.sync.PairRequest
import com.rabbeat.app.sync.PairResponse
import com.rabbeat.app.sync.SyncError
import com.rabbeat.app.sync.SyncHello
import com.rabbeat.app.sync.SyncJson
import com.rabbeat.app.sync.SyncPlaylist
import com.rabbeat.app.sync.SyncTrack
import com.rabbeat.app.sync.applySyncLikes
import com.rabbeat.app.sync.applySyncPlaylists
import com.rabbeat.app.sync.audioExt
import com.rabbeat.app.sync.coverUrl
import com.rabbeat.app.sync.syncFileName
import com.rabbeat.app.sync.syncKeys
import com.rabbeat.app.sync.syncNeteaseId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class DeviceSyncState(val running: Boolean = false, val message: String = "")

/**
 * Phone side of a same-Wi-Fi playlist sync. The computer hosts the session.
 * Songs that belong to a playlist and are missing on the other side are sent
 * with their audio, cover, and lyrics. Songs outside every playlist stay put.
 */
@Singleton
class DeviceSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: RabBeatDatabase,
    private val orderClock: PlaylistOrderClock,
) {
    fun lastHost(): String = prefs().getString(KEY_HOST, "").orEmpty()

    /** Removes songs copied from the computer so a later local scan can take their place. */
    suspend fun clearReceived(): Int = withContext(Dispatchers.IO) {
        val synced = db.trackDao().getBySource(SOURCE_ID)
        val adopted = db.trackDao().inSyncFolder().filter { track ->
            track.sourceId != SOURCE_ID && track.id.startsWith("ne_")
        }
        val removeIds = synced.map { it.id }
        db.inTransaction {
            removeIds.chunked(400).forEach { ids ->
                db.playlistDao().deleteRefs(ids)
                db.lyricLinkDao().deleteForTracks(ids)
                db.playHistoryDao().deleteForTracks(ids)
            }
            if (removeIds.isNotEmpty()) db.trackDao().deleteBySource(SOURCE_ID)
            adopted.forEach { track ->
                val songId = track.id.removePrefix("ne_")
                if (songId.isEmpty() || songId.any { !it.isDigit() }) return@forEach
                db.trackDao().updateUri(track.id, NeteaseMapper.streamUrl(songId.toLong()))
                db.trackDao().updateOrigin(track.id, TrackOrigin.REMOTE)
                db.trackDao().updateDownloadState(track.id, DownloadState.NONE)
            }
            db.albumDao().deleteOrphans()
            db.artistDao().deleteOrphans()
        }
        synced.forEach { track ->
            listOf(
                File(context.cacheDir, "lyrics/${track.id}.lrc"),
                File(context.filesDir, "lyrics/${track.id}.lrc"),
            ).forEach { file -> if (file.isFile) file.delete() }
        }
        File(context.filesDir, "sync").listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        synced.size + adopted.size
    }

    suspend fun run(hosts: List<String>, code: String, onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val pin = code.trim()
        if (pin.length != 6 || pin.any { !it.isDigit() }) error("配对码是 6 位数字")
        if (hosts.isEmpty()) error("二维码里没有地址")
        var last: IllegalStateException? = null
        var connected: Triple<LanHttp, String, String>? = null
        for (hostPort in hosts) {
            val (host, port) = parseEndpoint(hostPort)
            val http = LanHttp(host, port)
            onProgress("正在连接 $host:$port")
            val token = try {
                val body = http.post("/v1/pair", null, SyncJson.encodeToString(PairRequest(pin)))
                SyncJson.decodeFromString<PairResponse>(body).token
            } catch (error: IllegalStateException) {
                if (error.message == "配对码不对") throw error
                last = error
                continue
            } catch (error: Exception) {
                last = IllegalStateException(networkMessage(error))
                continue
            }
            connected = Triple(http, token, "$host:$port")
            break
        }
        val (http, token, savedHost) = connected ?: throw (last ?: IllegalStateException("连不上电脑"))
        prefs().edit().putString(KEY_HOST, savedHost).apply()
        onProgress("正在读取这台手机上的歌曲")
        val hello = buildHello()
        coroutineContext.ensureActive()
        onProgress("正在同步歌单")
        val plan = try {
            val body = http.post("/v1/sync", token, SyncJson.encodeToString(hello))
            SyncJson.decodeFromString<com.rabbeat.app.sync.SyncPlan>(body)
        } catch (error: Exception) {
            throw IllegalStateException(networkMessage(error))
        }
        plan.adopt.forEach { adopt ->
            val track = trackFor(adopt.contentKey) ?: return@forEach
            val keep = track.contentKey?.takeIf { it.isNotBlank() } ?: adopt.contentKey
            db.trackDao().setIdentity(track.id, keep, adopt.syncId)
        }
        var skipped = 0
        plan.push.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress("发给电脑 ${index + 1}/${plan.push.size} ${item.title}")
            val track = trackFor(item.contentKey, item.aliasKeys) ?: return@forEachIndexed
            if (!transfer { upload(http, token, item.syncId, track) }) skipped++
        }
        val dir = File(context.filesDir, "sync").apply { mkdirs() }
        var added = 0
        plan.pull.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress("从电脑接收 ${index + 1}/${plan.pull.size} ${item.title}")
            val dest = File(dir, syncFileName(item))
            val got = if (dest.isFile && dest.length() > 0L) {
                true
            } else {
                transfer { http.getToFile("/v1/file/${item.syncId}", token, dest) }
            }
            if (!got) {
                skipped++
                dest.delete()
                return@forEachIndexed
            }
            val claimed = listOf(item.contentKey) + item.aliasKeys
            if (claimed.none { it.startsWith("ne:") } && runCatching { ContentKey.of(dest) }.getOrNull() !in claimed) {
                dest.delete()
                skipped++
                return@forEachIndexed
            }
            if (storeReceived(dest, item, plan.likes.contains(item.syncId))) added++
        }
        plan.artPush.forEachIndexed { index, syncId ->
            coroutineContext.ensureActive()
            onProgress("发给电脑封面 ${index + 1}/${plan.artPush.size}")
            val track = db.trackDao().bySyncId(syncId) ?: return@forEachIndexed
            val bytes = artBytes(track) ?: return@forEachIndexed
            if (!transfer { http.putBytes("/v1/art/$syncId", token, bytes, "application/octet-stream") }) skipped++
        }
        plan.lyricPush.forEachIndexed { index, syncId ->
            coroutineContext.ensureActive()
            onProgress("发给电脑歌词 ${index + 1}/${plan.lyricPush.size}")
            val track = db.trackDao().bySyncId(syncId) ?: return@forEachIndexed
            val text = lyricText(track).orEmpty()
            if (text.isBlank() && track.lyricOffsetMs == 0L) return@forEachIndexed
            val sent = transfer {
                http.putBytes(
                    "/v1/lyric/$syncId",
                    token,
                    text.toByteArray(Charsets.UTF_8),
                    "text/plain; charset=utf-8",
                    mapOf("X-Lyric-Offset" to track.lyricOffsetMs.toString()),
                )
            }
            if (!sent) skipped++
        }
        plan.artPull.forEachIndexed { index, syncId ->
            coroutineContext.ensureActive()
            onProgress("从电脑接收封面 ${index + 1}/${plan.artPull.size}")
            val saved = transfer {
                val (bytes, _) = http.getBytes("/v1/art/$syncId", token)
                storeArt(syncId, bytes)
            }
            if (!saved) skipped++
        }
        plan.lyricPull.forEachIndexed { index, syncId ->
            coroutineContext.ensureActive()
            onProgress("从电脑接收歌词 ${index + 1}/${plan.lyricPull.size}")
            val saved = transfer {
                val (bytes, headers) = http.getBytes("/v1/lyric/$syncId", token)
                val offset = headers["x-lyric-offset"]?.toLongOrNull() ?: 0L
                storeLyric(syncId, bytes.toString(Charsets.UTF_8), offset)
            }
            if (!saved) skipped++
        }
        plan.artLinks.forEach { link ->
            val track = db.trackDao().bySyncId(link.syncId) ?: return@forEach
            rememberArtUrl(track, link.url)
        }
        applySyncPlaylists(db.playlistDao(), db.trackDao(), plan.playlists)
        applySyncLikes(db.playlistDao(), db.trackDao(), plan.likes, plan.likesUpdatedAt)
        plan.coverPush.forEachIndexed { index, syncId ->
            coroutineContext.ensureActive()
            onProgress("发给电脑歌单封面 ${index + 1}/${plan.coverPush.size}")
            val playlist = db.playlistDao().bySyncId(syncId) ?: return@forEachIndexed
            val bytes = playlistCoverBytes(playlist.coverUri) ?: return@forEachIndexed
            if (!transfer { http.putBytes("/v1/playlist-cover/$syncId", token, bytes, "application/octet-stream") }) skipped++
        }
        plan.coverPull.forEachIndexed { index, syncId ->
            coroutineContext.ensureActive()
            onProgress("从电脑接收歌单封面 ${index + 1}/${plan.coverPull.size}")
            val saved = transfer {
                val (bytes, _) = http.getBytes("/v1/playlist-cover/$syncId", token)
                storePlaylistCover(syncId, bytes)
            }
            if (!saved) skipped++
        }
        runCatching { http.post("/v1/finish", token, "{}") }
        val miss = if (skipped > 0) "，有 $skipped 项没传过来" else ""
        onProgress("完成：同步 ${plan.playlists.size} 个歌单，这台手机新增 $added 首$miss")
    }

    private suspend fun transfer(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    private suspend fun buildHello(): SyncHello {
        val tracks = mutableListOf<SyncTrack>()
        val idByTrack = mutableMapOf<String, String>()
        db.trackDao().allVisible().forEach { track ->
            val opened = openable(track)
            try {
                val neteaseKey = track.syncNeteaseId()?.let(ContentKey::netease)
                val fileKey = if (neteaseKey == null) opened?.let { runCatching { hashAudio(it) }.getOrNull() } else null
                val keys = syncKeys(neteaseKey, track.contentKey, fileKey)
                    ?: return@forEach
                val syncId = track.syncId ?: ContentKey.newSyncId()
                if (track.contentKey != keys.remember || track.syncId != syncId) {
                    db.trackDao().setIdentity(track.id, keys.remember, syncId)
                }
                val size = opened?.length ?: track.fileSize ?: 0L
                tracks += SyncTrack(
                    syncId = syncId,
                    contentKey = keys.contentKey,
                    aliasKeys = keys.aliasKeys,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    size = size,
                    ext = audioExt(opened?.name, track.mimeType),
                    hasFile = opened != null && size > 0L,
                    favorite = track.isFavorite,
                    hasArt = hasLocalArt(track),
                    hasLyric = hasLocalLyric(track),
                    lyricOffsetMs = track.lyricOffsetMs,
                    artUrl = if (hasLocalArt(track)) null else coverUrl(track.artworkUri),
                )
                idByTrack[track.id] = syncId
            } finally {
                opened?.close()
            }
        }
        val playlists = db.playlistDao().getAll().filter { !it.isSystem }.map { playlist ->
            val syncId = playlist.syncId ?: ContentKey.newSyncId().also { db.playlistDao().setSyncId(playlist.id, it) }
            val coverFile = playlistCoverFile(playlist.coverUri)
            SyncPlaylist(
                syncId = syncId,
                name = playlist.name,
                updatedAt = playlist.updatedAt,
                sortOrder = playlist.sortOrder,
                trackSyncIds = db.playlistDao().refs(playlist.id).mapNotNull { idByTrack[it.trackId] },
                hasCover = coverFile != null || playlist.coverUri?.startsWith("content:") == true,
                coverUrl = if (coverFile != null) null else coverUrl(playlist.coverUri),
            )
        }
        val memberOrder = db.playlistDao().refs(LikedPlaylist.ID).mapNotNull { idByTrack[it.trackId] }
        val seen = memberOrder.toSet()
        val likes = memberOrder + tracks.filter { it.favorite && it.syncId !in seen }.map { it.syncId }
        return SyncHello(
            deviceName = "phone",
            tracks = tracks,
            playlists = playlists,
            likes = likes,
            likesUpdatedAt = db.playlistDao().get(LikedPlaylist.ID)?.updatedAt ?: 0L,
            playlistOrderAt = orderClock.get(),
        )
    }

    private suspend fun trackFor(contentKey: String, aliasKeys: List<String> = emptyList(), syncId: String? = null): TrackEntity? {
        db.trackDao().byContentKey(contentKey)?.let { return it }
        aliasKeys.forEach { key ->
            if (key.isNotBlank()) db.trackDao().byContentKey(key)?.let { return it }
        }
        val candidates = listOf(contentKey) + aliasKeys
        for (key in candidates) {
            if (!key.startsWith("ne:")) continue
            val songId = key.removePrefix("ne:")
            if (songId.isNotEmpty() && songId.all { it.isDigit() }) {
                db.trackDao().get("ne_$songId")?.let { return it }
            }
        }
        if (!syncId.isNullOrBlank()) return db.trackDao().bySyncId(syncId)
        return null
    }

    private suspend fun storeReceived(file: File, meta: SyncTrack, liked: Boolean): Boolean {
        val existing = trackFor(meta.contentKey, meta.aliasKeys, meta.syncId)
        if (existing != null) {
            val current = openable(existing)
            val hasFile = current != null
            current?.close()
            if (!hasFile) {
                db.trackDao().updateUri(existing.id, file.toURI().toString())
                db.trackDao().updateOrigin(existing.id, if (existing.id.startsWith("ne_")) TrackOrigin.CACHED else TrackOrigin.LOCAL)
                db.trackDao().updateDownloadState(existing.id, DownloadState.DOWNLOADED)
            } else if (existing.uri != file.toURI().toString()) {
                file.delete()
            }
            val keep = existing.contentKey?.takeIf { it.isNotBlank() } ?: meta.contentKey
            if (existing.contentKey != keep || existing.syncId != meta.syncId) {
                db.trackDao().setIdentity(existing.id, keep, meta.syncId)
            }
            if ((meta.favorite || liked) && !existing.isFavorite) db.trackDao().setFavorite(existing.id, true)
            rememberArtUrl(existing, meta.artUrl)
            return !hasFile
        }
        ensureSource()
        val now = System.currentTimeMillis()
        val artists = db.artistDao().getAll()
        val albums = db.albumDao().getAll()
        val artist = artists.firstOrNull { it.name.equals(meta.artist, ignoreCase = true) }
        val artistId = artist?.id ?: ("devart_" + ContentKey.sha1(meta.artist.lowercase()).take(16))
        val album = albums.firstOrNull { it.artist.equals(meta.artist, ignoreCase = true) && it.title.equals(meta.album, ignoreCase = true) }
        val albumId = album?.id ?: ("devalb_" + ContentKey.sha1("${meta.artist.lowercase()}|${meta.album.lowercase()}").take(16))
        db.trackDao().upsertAll(
            listOf(
                TrackEntity(
                    id = "syn_${meta.syncId}",
                    sourceId = SOURCE_ID,
                    remoteId = null,
                    title = meta.title,
                    artist = meta.artist,
                    artistId = artistId,
                    album = meta.album,
                    albumId = albumId,
                    durationMs = meta.durationMs,
                    trackNumber = null,
                    discNumber = null,
                    year = null,
                    mimeType = mimeOf(meta.ext),
                    bitrate = null,
                    sampleRate = null,
                    fileSize = file.length(),
                    uri = file.toURI().toString(),
                    artworkUri = coverUrl(meta.artUrl),
                    origin = TrackOrigin.LOCAL,
                    downloadState = DownloadState.DOWNLOADED,
                    isFavorite = meta.favorite || liked,
                    playCount = 0,
                    lastPlayedAt = null,
                    addedAt = now,
                    folder = "来自电脑",
                    contentKey = meta.contentKey,
                    syncId = meta.syncId,
                ),
            ),
        )
        if (artists.none { it.id == artistId }) {
            db.artistDao().upsertAll(
                listOf(ArtistEntity(artistId, SOURCE_ID, meta.artist, null, 1, 1)),
            )
        }
        if (albums.none { it.id == albumId }) {
            db.albumDao().upsertAll(
                listOf(AlbumEntity(albumId, SOURCE_ID, meta.album, meta.artist, null, coverUrl(meta.artUrl), 1, now)),
            )
        }
        return true
    }

    private suspend fun rememberArtUrl(track: TrackEntity, raw: String?) {
        val url = coverUrl(raw) ?: return
        if (hasLocalArt(track) || !track.artworkUri.isNullOrBlank()) return
        db.trackDao().updateArtworkUri(track.id, url)
        val albumId = track.albumId
        if (albumId != null) {
            val album = db.albumDao().get(albumId)
            if (album != null && album.artworkUri.isNullOrBlank()) db.albumDao().updateArtworkUri(album.id, url)
        }
    }

    private fun playlistCoverFile(uri: String?): File? {
        if (uri == null || !uri.startsWith("file:")) return null
        val file = runCatching { File(URI(uri)) }.getOrNull() ?: return null
        return file.takeIf { it.isFile && it.length() in 32..MAX_ART.toLong() }
    }

    private fun playlistCoverBytes(uri: String?): ByteArray? {
        playlistCoverFile(uri)?.let { file ->
            return runCatching { file.readBytes() }.getOrNull()?.takeIf { it.size in 32..MAX_ART }
        }
        if (uri == null || !uri.startsWith("content:")) return null
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        return runCatching { context.contentResolver.openInputStream(parsed)?.use { it.readBytes() } }
            .getOrNull()
            ?.takeIf { it.size in 32..MAX_ART }
    }

    private suspend fun storePlaylistCover(syncId: String, bytes: ByteArray) {
        if (bytes.size !in 32..MAX_ART) return
        val playlist = db.playlistDao().bySyncId(syncId) ?: return
        val dir = File(context.filesDir, "playlist_covers").apply { mkdirs() }
        val sanitized = playlist.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val ext = if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "png" else "jpg"
        val target = File(dir, "cover_${sanitized}_${System.currentTimeMillis()}.$ext")
        target.writeBytes(bytes)
        dir.listFiles()?.forEach { file ->
            if (file != target && file.name.startsWith("cover_${sanitized}_")) file.delete()
        }
        db.playlistDao().setCover(playlist.id, Uri.fromFile(target).toString())
    }

    private suspend fun storeArt(syncId: String, bytes: ByteArray) {
        if (bytes.size !in 32..MAX_ART) return
        val track = db.trackDao().bySyncId(syncId) ?: return
        if (hasLocalArt(track)) return
        val uri = com.rabbeat.app.util.ArtworkCache.write(context, track.id, bytes) ?: return
        db.trackDao().updateArtworkUri(track.id, uri)
        val albumId = track.albumId
        if (albumId != null) {
            val album = db.albumDao().get(albumId)
            if (album != null && album.artworkUri.isNullOrBlank()) db.albumDao().updateArtworkUri(album.id, uri)
        }
    }

    private suspend fun storeLyric(syncId: String, text: String, offset: Long) {
        val track = db.trackDao().bySyncId(syncId) ?: return
        val cleaned = text.trim()
        if (cleaned.isNotEmpty() && !hasLocalLyric(track)) {
            val file = File(context.cacheDir, "lyrics/${track.id}.lrc")
            file.parentFile?.mkdirs()
            file.writeText(cleaned, Charsets.UTF_8)
            db.trackDao().updateLyricUri(track.id, Uri.fromFile(file).toString())
        }
        if (offset != 0L && track.lyricOffsetMs == 0L) db.trackDao().updateLyricOffset(track.id, offset)
    }

    private fun hasLocalArt(track: TrackEntity): Boolean {
        val raw = track.artworkUri ?: return false
        if (raw.startsWith("file:")) {
            val file = runCatching { File(URI(raw)) }.getOrNull() ?: return false
            return file.isFile && file.length() in 32..MAX_ART.toLong()
        }
        return raw.startsWith("content:")
    }

    private fun hasLocalLyric(track: TrackEntity): Boolean {
        if (textFileExists(track.lyricUri)) return true
        if (track.uri.startsWith("file:")) {
            val audio = runCatching { File(URI(track.uri)) }.getOrNull()
            val sidecar = audio?.parentFile?.let { File(it, audio.nameWithoutExtension + ".lrc") }
            if (sidecar != null && sidecar.isFile && sidecar.length() in 1..MAX_LYRIC.toLong()) return true
        }
        return lyricCacheFile(track.id)?.let { it.isFile && it.length() in 1..MAX_LYRIC.toLong() } == true
    }

    private fun lyricCacheFile(id: String): File? {
        val names = listOf(
            File(context.cacheDir, "lyrics/$id.lrc"),
            File(context.filesDir, "lyrics/$id.lrc"),
        )
        return names.firstOrNull { it.isFile }
    }

    private fun textFileExists(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        if (raw.startsWith("content:")) return true
        if (!raw.startsWith("file:")) return false
        val file = runCatching { File(URI(raw)) }.getOrNull() ?: return false
        return file.isFile && file.length() in 1..MAX_LYRIC.toLong()
    }

    private fun artBytes(track: TrackEntity): ByteArray? {
        val raw = track.artworkUri ?: return null
        if (!raw.startsWith("file:") && !raw.startsWith("content:")) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val bytes = when (uri.scheme) {
            "file" -> uri.path?.let { path -> runCatching { File(path).takeIf { it.isFile }?.readBytes() }.getOrNull() }
            else -> runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        } ?: return null
        return bytes.takeIf { it.size in 32..MAX_ART }
    }

    private fun lyricText(track: TrackEntity): String? {
        track.lyricUri?.let { raw ->
            readTextUri(raw)?.let { return it }
        }
        if (track.uri.startsWith("file:")) {
            val audio = runCatching { File(URI(track.uri)) }.getOrNull()
            val sidecar = audio?.parentFile?.let { File(it, audio.nameWithoutExtension + ".lrc") }
            if (sidecar != null && sidecar.isFile && sidecar.length() in 1..MAX_LYRIC.toLong()) {
                return runCatching { sidecar.readText(Charsets.UTF_8).trim().ifBlank { null } }.getOrNull()
            }
        }
        val cached = lyricCacheFile(track.id)
        if (cached != null && cached.length() in 1..MAX_LYRIC.toLong()) {
            return runCatching { cached.readText(Charsets.UTF_8).trim().ifBlank { null } }.getOrNull()
        }
        return null
    }

    private fun readTextUri(raw: String): String? {
        if (!raw.startsWith("file:") && !raw.startsWith("content:")) return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val bytes = when (uri.scheme) {
            "file" -> uri.path?.let { path ->
                val file = File(path)
                if (!file.isFile || file.length() !in 1..MAX_LYRIC.toLong()) null
                else runCatching { file.readBytes() }.getOrNull()
            }
            else -> runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.takeIf { it.size in 1..MAX_LYRIC }
        } ?: return null
        return bytes.toString(Charsets.UTF_8).trim().ifBlank { null }
    }

    private suspend fun ensureSource() {
        if (db.sourceDao().get(SOURCE_ID) != null) return
        val now = System.currentTimeMillis()
        db.sourceDao().upsert(
            SourceEntity(
                id = SOURCE_ID,
                type = SourceType.LOCAL,
                displayName = "从电脑同步",
                configJson = "{}",
                status = SourceStatus.CONNECTED,
                lastSyncAt = now,
                createdAt = now,
            ),
        )
    }

    private fun upload(http: LanHttp, token: String, syncId: String, track: TrackEntity) {
        val audio = openable(track) ?: return
        try {
            http.put("/v1/file/$syncId", token, audio.length, audio.open())
        } finally {
            audio.close()
        }
    }

    private fun openable(track: TrackEntity): OpenAudio? {
        if (track.uri.startsWith("file:")) {
            val file = runCatching { File(URI(track.uri)) }.getOrNull()
            if (file != null && file.isFile && file.length() > 0L) return OpenAudio.FileAudio(file)
        }
        if (track.uri.startsWith("content:")) {
            val uri = Uri.parse(track.uri)
            val pfd = runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull() ?: return null
            val size = runCatching { pfd.statSize }.getOrDefault(-1L)
            if (size <= 0L) {
                pfd.close()
                return null
            }
            return OpenAudio.ContentAudio(context, uri, pfd, size, track.title)
        }
        return null
    }

    private fun hashAudio(audio: OpenAudio): String = when (audio) {
        is OpenAudio.FileAudio -> ContentKey.of(audio.file)
        is OpenAudio.ContentAudio -> hashChannel(audio.held)
    }

    private fun hashChannel(pfd: ParcelFileDescriptor): String {
        FileInputStream(pfd.fileDescriptor).channel.use { channel ->
            val size = channel.size().takeIf { it > 0L } ?: pfd.statSize
            if (size <= 0L) error("读不到文件大小")
            val head = readAt(channel, 0, minOf(ContentKey.CHUNK.toLong(), size).toInt())
            val tailLen = minOf(ContentKey.CHUNK.toLong(), size).toInt()
            val tail = readAt(channel, maxOf(0L, size - tailLen), tailLen)
            return ContentKey.of(size, head, tail)
        }
    }

    private fun readAt(channel: FileChannel, offset: Long, length: Int): ByteArray {
        val bytes = ByteArray(length)
        val buffer = ByteBuffer.wrap(bytes)
        channel.position(offset)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) error("文件读不完整")
        }
        return bytes
    }

    private fun parseEndpoint(raw: String): Pair<String, Int> {
        val text = raw.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        if (text.isBlank()) error("地址不对")
        val colon = text.lastIndexOf(':')
        val host: String
        val port: Int
        if (colon > 0 && text.substring(colon + 1).all { it.isDigit() }) {
            host = text.substring(0, colon)
            port = text.substring(colon + 1).toInt()
        } else {
            host = text
            port = 28631
        }
        if (host.isBlank() || port !in 1..65535) error("地址不对")
        return host to port
    }

    private fun networkMessage(error: Throwable): String = when {
        error is IllegalStateException && !error.message.isNullOrBlank() -> error.message!!
        error is ConnectException || error.message?.contains("refused", true) == true ->
            "连不上电脑。确认地址，以及电脑设置里正开着同步"
        error is UnknownHostException -> "找不到这个地址"
        else -> "传输中断：${error.message ?: "网络错误"}"
    }

    private fun mimeOf(ext: String): String = when (ext) {
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        else -> "audio/mpeg"
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private sealed interface OpenAudio {
        val length: Long
        val name: String
        fun open(): InputStream
        fun close()

        class FileAudio(val file: File) : OpenAudio {
            override val length: Long = file.length()
            override val name: String = file.name
            override fun open(): InputStream = file.inputStream()
            override fun close() = Unit
        }

        class ContentAudio(
            private val context: Context,
            private val uri: Uri,
            val held: ParcelFileDescriptor,
            override val length: Long,
            title: String,
        ) : OpenAudio {
            override val name: String = "$title.audio"
            override fun open(): InputStream = context.contentResolver.openInputStream(uri)
                ?: error("打不开这首歌")
            override fun close() { held.close() }
        }
    }

    private inner class LanHttp(private val host: String, private val port: Int) {
        fun post(path: String, token: String?, json: String): String {
            val bytes = json.toByteArray(Charsets.UTF_8)
            return exchange("POST", path, token, bytes.size.toLong(), "application/json", { it.write(bytes) })
        }

        fun put(path: String, token: String, length: Long, body: InputStream) {
            body.use { input ->
                exchange("PUT", path, token, length, "application/octet-stream", { out -> input.copyTo(out) })
            }
        }

        fun putBytes(path: String, token: String, bytes: ByteArray, contentType: String, extra: Map<String, String> = emptyMap()) {
            exchange("PUT", path, token, bytes.size.toLong(), contentType, { it.write(bytes) }, extra)
        }

        fun getBytes(path: String, token: String): Pair<ByteArray, Map<String, String>> {
            val out = ByteArrayOutputStream()
            var headers = emptyMap<String, String>()
            exchange("GET", path, token, -1, null, null) { input, length, hdrs ->
                headers = hdrs
                if (length >= 0) copyExactly(input, out, length) else input.copyTo(out)
            }
            return out.toByteArray() to headers
        }

        fun getToFile(path: String, token: String, dest: File) {
            val temp = File(dest.parentFile, dest.name + ".part")
            try {
                exchange("GET", path, token, -1, null, null) { input, length, _ ->
                    FileOutputStream(temp).use { output ->
                        if (length >= 0) copyExactly(input, output, length) else input.copyTo(output)
                    }
                }
                if (temp.length() <= 0L) error("电脑发来的文件是空的")
                if (dest.exists()) dest.delete()
                if (!temp.renameTo(dest)) temp.copyTo(dest, overwrite = true)
            } catch (error: Exception) {
                temp.delete()
                throw error
            } finally {
                temp.delete()
            }
        }

        private fun exchange(
            method: String,
            path: String,
            token: String?,
            length: Long,
            contentType: String?,
            writeBody: ((OutputStream) -> Unit)?,
            extraHeaders: Map<String, String> = emptyMap(),
            readBody: ((InputStream, Long, Map<String, String>) -> Unit)? = null,
        ): String {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), 8_000)
                socket.soTimeout = 60_000
                val output = socket.getOutputStream()
                val header = buildString {
                    append("$method $path HTTP/1.1\r\n")
                    append("Host: $host:$port\r\n")
                    append("Connection: close\r\n")
                    if (!token.isNullOrBlank()) append("Authorization: Bearer $token\r\n")
                    if (length >= 0 && writeBody != null) {
                        append("Content-Type: ${contentType ?: "application/octet-stream"}\r\n")
                        append("Content-Length: $length\r\n")
                    }
                    extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
                    append("\r\n")
                }
                output.write(header.toByteArray(Charsets.US_ASCII))
                writeBody?.invoke(output)
                output.flush()
                val input = BufferedInputStream(socket.getInputStream())
                val (status, headers) = readHeaders(input)
                val responseLength = headers["content-length"]?.toLongOrNull() ?: -1L
                if (readBody != null) {
                    if (status !in 200..299) {
                        val payload = if (responseLength >= 0) readExactly(input, responseLength) else input.readBytes()
                        error(errorText(status, payload.toString(Charsets.UTF_8)))
                    }
                    readBody(input, responseLength, headers)
                    return ""
                }
                val payload = if (responseLength >= 0) readExactly(input, responseLength) else input.readBytes()
                val text = payload.toString(Charsets.UTF_8)
                if (status !in 200..299) error(errorText(status, text))
                return text
            } catch (error: IllegalStateException) {
                throw error
            } catch (error: Exception) {
                throw IllegalStateException(networkMessage(error), error)
            } finally {
                socket.close()
            }
        }

        private fun errorText(status: Int, body: String): String {
            val parsed = runCatching { SyncJson.decodeFromString<SyncError>(body).error }.getOrNull()
            return parsed?.takeIf { it.isNotBlank() } ?: if (status == 401) "配对码不对" else "电脑返回 $status"
        }

        private fun readHeaders(input: InputStream): Pair<Int, Map<String, String>> {
            val raw = ByteArrayOutputStream()
            while (true) {
                val value = input.read()
                if (value < 0) error("连接中断")
                raw.write(value)
                val size = raw.size()
                if (size > 65_536) error("响应头过大")
                if (size >= 4) {
                    val bytes = raw.toByteArray()
                    if (bytes[size - 4] == '\r'.code.toByte() && bytes[size - 3] == '\n'.code.toByte() &&
                        bytes[size - 2] == '\r'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()
                    ) {
                        break
                    }
                }
            }
            val lines = raw.toString(Charsets.ISO_8859_1.name()).split("\r\n")
            val status = lines.first().split(" ").getOrNull(1)?.toIntOrNull() ?: error("无法识别响应")
            val headers = lines.drop(1).mapNotNull { line ->
                val index = line.indexOf(':')
                if (index <= 0) null else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
            }.toMap()
            return status to headers
        }

        private fun readExactly(input: InputStream, length: Long): ByteArray {
            val out = ByteArrayOutputStream()
            copyExactly(input, out, length)
            return out.toByteArray()
        }

        private fun copyExactly(input: InputStream, output: OutputStream, length: Long) {
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) error("文件传了一半就断了")
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }

    private companion object {
        const val SOURCE_ID = "src_sync"
        const val PREFS = "device_sync"
        const val KEY_HOST = "host"
        const val MAX_ART = 12 * 1024 * 1024
        const val MAX_LYRIC = 1024 * 1024
    }
}
