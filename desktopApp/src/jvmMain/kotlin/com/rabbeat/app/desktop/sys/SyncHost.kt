package com.rabbeat.app.desktop.sys

import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.desktop.library.LibraryScanner
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
import com.rabbeat.app.sync.SyncMerge
import com.rabbeat.app.sync.SyncPlan
import com.rabbeat.app.sync.SyncTrack
import com.rabbeat.app.sync.applySyncLikes
import com.rabbeat.app.sync.applySyncPlaylists
import com.rabbeat.app.sync.audioExt
import com.rabbeat.app.sync.coverUrl
import com.rabbeat.app.sync.syncFileName
import com.rabbeat.app.sync.syncKeys
import com.rabbeat.app.sync.syncNeteaseId
import com.rabbeat.app.util.NcmDecoderCore
import com.rabbeat.app.util.QmcDecoderCore
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URI
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * Desktop side of a same-Wi-Fi sync. The phone connects here, pulls songs it
 * lacks, and uploads songs the computer lacks.
 */
class SyncHost(
    private val db: RabBeatDatabase,
    private val settings: DesktopSettings,
) {
    val running = MutableStateFlow(false)
    val code = MutableStateFlow("")
    val addresses = MutableStateFlow<List<String>>(emptyList())
    val status = MutableStateFlow("未开始")

    private var server: HttpServer? = null
    private var pool: java.util.concurrent.ExecutorService? = null
    private var token: String? = null
    private var plan: SyncPlan? = null
    private var files: Map<String, File> = emptyMap()
    private var incoming: Map<String, SyncTrack> = emptyMap()
    private var sent = 0
    private var received = 0

    fun start() {
        if (running.value) return
        stop()
        try {
            val bound = (28631..28640).firstNotNullOfOrNull { port ->
                runCatching { HttpServer.create(InetSocketAddress("0.0.0.0", port), 0) to port }.getOrNull()
            }
            if (bound == null) {
                status.value = "端口被占用，启动不了"
                return
            }
            val (http, port) = bound
            token = null
            plan = null
            files = emptyMap()
            incoming = emptyMap()
            sent = 0
            received = 0
            code.value = (100000 + Random.nextInt(900000)).toString()
            addresses.value = lanAddresses(port)
            val workers = Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "rabbeat-sync").apply { isDaemon = true }
            }
            http.executor = workers
            http.createContext("/") { exchange ->
                try {
                    route(exchange)
                } catch (error: Exception) {
                    DesktopLog.e("Sync", "${error.javaClass.simpleName}: ${error.message ?: "request failed"}")
                    runCatching { exchange.reply(500, SyncJson.encodeToString(SyncError("同步中断"))) }
                } finally {
                    runCatching { exchange.close() }
                }
            }
            http.start()
            server = http
            pool = workers
            running.value = true
            status.value = if (addresses.value.isEmpty()) "没有局域网地址，手机连不上" else "等待手机连接"
            DesktopLog.i("Sync", "listening ${addresses.value} code ${code.value}")
        } catch (error: Throwable) {
            stop()
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            status.value = "同步开不了：$detail"
            DesktopLog.e("Sync", status.value)
        }
    }

    fun stop() {
        server?.stop(0)
        pool?.shutdownNow()
        server = null
        pool = null
        token = null
        if (running.value) status.value = "已停止"
        running.value = false
    }

    private fun route(exchange: HttpExchange) {
        val path = exchange.requestURI.path.orEmpty()
        val method = exchange.requestMethod.uppercase()
        when {
            method == "POST" && path == "/v1/pair" -> pair(exchange)
            method == "POST" && path == "/v1/sync" -> acceptCatalog(exchange)
            method == "GET" && path.startsWith("/v1/file/") -> sendFile(exchange, path)
            method == "PUT" && path.startsWith("/v1/file/") -> receiveFile(exchange, path)
            method == "GET" && path.startsWith("/v1/art/") -> sendArt(exchange, path)
            method == "PUT" && path.startsWith("/v1/art/") -> receiveArt(exchange, path)
            method == "GET" && path.startsWith("/v1/lyric/") -> sendLyric(exchange, path)
            method == "PUT" && path.startsWith("/v1/lyric/") -> receiveLyric(exchange, path)
            method == "GET" && path.startsWith("/v1/playlist-cover/") -> sendPlaylistCover(exchange, path)
            method == "PUT" && path.startsWith("/v1/playlist-cover/") -> receivePlaylistCover(exchange, path)
            method == "POST" && path == "/v1/finish" -> finish(exchange)
            else -> exchange.reply(404, SyncJson.encodeToString(SyncError("没有这个接口")))
        }
    }

    private fun pair(exchange: HttpExchange) {
        val request = runCatching {
            SyncJson.decodeFromString<PairRequest>(exchange.requestBody.readBytes().decodeToString())
        }.getOrNull()
        if (request == null || request.code != code.value) {
            exchange.reply(401, SyncJson.encodeToString(SyncError("配对码不对")))
            return
        }
        val issued = ContentKey.newSyncId()
        token = issued
        status.value = "手机已连接，正在同步歌单"
        exchange.reply(200, SyncJson.encodeToString(PairResponse(issued)))
    }

    private fun acceptCatalog(exchange: HttpExchange) {
        if (!authorized(exchange)) return
        val hello = runCatching {
            SyncJson.decodeFromString<SyncHello>(exchange.requestBody.readBytes().decodeToString())
        }.getOrElse {
            exchange.reply(400, SyncJson.encodeToString(SyncError("曲库目录读不了")))
            return
        }
        val built = dbCall { buildCatalog() }
        val next = SyncMerge.plan(
            built.tracks,
            hello.tracks,
            built.playlists,
            hello.playlists,
            built.likes,
            hello.likes,
            settings.playlistOrderAt(),
            hello.playlistOrderAt,
            built.likesAt,
            hello.likesUpdatedAt,
        )
        plan = next
        files = built.files
        dbCall {
            applySyncPlaylists(db.playlistDao(), db.trackDao(), next.playlists)
            applyRemoteArtUrls(hello.tracks)
        }
        incoming = next.push.associateBy { it.syncId }
        sent = 0
        received = 0
        val covers = next.artPull.size + next.artPush.size
        val lyrics = next.lyricPull.size + next.lyricPush.size
        val playlistCovers = next.coverPull.size + next.coverPush.size
        val extra = buildString {
            if (covers > 0) append("，封面 $covers")
            if (lyrics > 0) append("，歌词 $lyrics")
            if (playlistCovers > 0) append("，歌单封面 $playlistCovers")
        }
        status.value = "歌单 ${next.playlists.size} 个，发给手机 ${next.pull.size} 首，从手机接收 ${next.push.size} 首$extra"
        exchange.reply(200, SyncJson.encodeToString(next))
    }

    private fun sendFile(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path) ?: run {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这首歌")))
            return
        }
        val file = files[syncId]
        if (file == null || !file.isFile) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("这首歌的文件不在了")))
            return
        }
        val title = plan?.pull?.firstOrNull { it.syncId == syncId }?.title ?: file.name
        sent += 1
        val total = plan?.pull?.size ?: sent
        status.value = "发给手机 $sent/$total $title"
        exchange.responseHeaders.set("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, file.length())
        file.inputStream().use { input -> exchange.responseBody.use { input.copyTo(it) } }
    }

    private fun receiveFile(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path) ?: run {
            exchange.requestBody.close()
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这首歌")))
            return
        }
        val meta = incoming[syncId]
        if (meta == null) {
            exchange.requestBody.close()
            exchange.reply(404, SyncJson.encodeToString(SyncError("这首歌不在同步列表里")))
            return
        }
        received += 1
        val total = plan?.push?.size ?: received
        status.value = "从手机接收 $received/$total ${meta.title}"
        val dest = File(incomingDir(), syncFileName(meta))
        val temp = File(dest.parentFile, dest.name + ".part")
        runCatching {
            temp.outputStream().use { output -> exchange.requestBody.use { it.copyTo(output) } }
            if (temp.length() <= 0L) error("空文件")
            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) temp.copyTo(dest, overwrite = true)
            temp.delete()
        }.onFailure {
            temp.delete()
            exchange.reply(500, SyncJson.encodeToString(SyncError("保存失败")))
            return
        }
        dbCall { storeReceived(dest, meta) }
        exchange.reply(200, "{}")
    }

    private fun sendArt(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path) ?: run {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这首歌")))
            return
        }
        val file = dbCall { db.trackDao().bySyncId(syncId)?.let(::artFile) }
        if (file == null) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("没有封面")))
            return
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        if (bytes == null || bytes.size !in 32..MAX_ART) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("没有封面")))
            return
        }
        exchange.responseHeaders.set("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun receiveArt(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path)
        val bytes = runCatching { exchange.requestBody.readBytes() }.getOrNull()
        if (syncId == null || bytes == null || bytes.size !in 32..MAX_ART) {
            exchange.reply(400, SyncJson.encodeToString(SyncError("封面不对")))
            return
        }
        val saved = dbCall { saveArt(syncId, bytes) }
        if (!saved) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这首歌")))
            return
        }
        exchange.reply(200, "{}")
    }

    private fun sendLyric(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path) ?: run {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这首歌")))
            return
        }
        val payload = dbCall {
            val track = db.trackDao().bySyncId(syncId) ?: return@dbCall null
            val text = lyricFile(track)?.let { runCatching { it.readText(Charsets.UTF_8) }.getOrNull() }.orEmpty()
            track.lyricOffsetMs to text
        }
        if (payload == null) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("没有歌词")))
            return
        }
        val bytes = payload.second.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_LYRIC) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("歌词太大")))
            return
        }
        exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.responseHeaders.set("X-Lyric-Offset", payload.first.toString())
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun receiveLyric(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path)
        val offset = exchange.requestHeaders.getFirst("X-Lyric-Offset")?.toLongOrNull() ?: 0L
        val bytes = runCatching { exchange.requestBody.readBytes() }.getOrNull()
        if (syncId == null || bytes == null || bytes.size > MAX_LYRIC) {
            exchange.reply(400, SyncJson.encodeToString(SyncError("歌词不对")))
            return
        }
        val text = bytes.toString(Charsets.UTF_8)
        val saved = dbCall { saveLyric(syncId, text, offset) }
        if (!saved) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这首歌")))
            return
        }
        exchange.reply(200, "{}")
    }

    private fun sendPlaylistCover(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path) ?: run {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这个歌单")))
            return
        }
        val file = dbCall { db.playlistDao().bySyncId(syncId)?.let { playlistCoverFile(it.coverUri) } }
        val bytes = file?.let { runCatching { it.readBytes() }.getOrNull() }
        if (bytes == null || bytes.size !in 32..MAX_ART) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("没有歌单封面")))
            return
        }
        exchange.responseHeaders.set("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun receivePlaylistCover(exchange: HttpExchange, path: String) {
        if (!authorized(exchange)) return
        val syncId = fileId(path)
        val bytes = runCatching { exchange.requestBody.readBytes() }.getOrNull()
        if (syncId == null || bytes == null || bytes.size !in 32..MAX_ART) {
            exchange.reply(400, SyncJson.encodeToString(SyncError("歌单封面不对")))
            return
        }
        val saved = dbCall { savePlaylistCover(syncId, bytes) }
        if (!saved) {
            exchange.reply(404, SyncJson.encodeToString(SyncError("找不到这个歌单")))
            return
        }
        exchange.reply(200, "{}")
    }

    private fun finish(exchange: HttpExchange) {
        if (!authorized(exchange)) return
        exchange.requestBody.close()
        val current = plan
        if (current != null) {
            dbCall {
                applySyncPlaylists(db.playlistDao(), db.trackDao(), current.playlists)
                applySyncLikes(db.playlistDao(), db.trackDao(), current.likes, current.likesUpdatedAt)
            }
        }
        status.value = "完成：同步 ${current?.playlists?.size ?: 0} 个歌单，电脑新增 $received 首，发给手机 $sent 首"
        DesktopLog.i("Sync", status.value)
        exchange.reply(200, "{}")
    }

    private fun authorized(exchange: HttpExchange): Boolean {
        val expected = token
        val header = exchange.requestHeaders.getFirst("Authorization")
        if (expected.isNullOrBlank() || header != "Bearer $expected") {
            exchange.reply(401, SyncJson.encodeToString(SyncError("还没配对")))
            return false
        }
        return true
    }

    private fun fileId(path: String): String? {
        val id = path.substringAfterLast('/')
        if (id.isBlank() || id.any { !it.isLetterOrDigit() }) return null
        return id
    }

    private data class Built(
        val tracks: List<SyncTrack>,
        val playlists: List<com.rabbeat.app.sync.SyncPlaylist>,
        val likes: List<String>,
        val likesAt: Long,
        val files: Map<String, File>,
    )

    private suspend fun buildCatalog(): Built {
        val needed = likedOrListedIds()
        val tracks = mutableListOf<SyncTrack>()
        val files = mutableMapOf<String, File>()
        val idByTrack = mutableMapOf<String, String>()
        db.trackDao().allVisible().forEach { track ->
            val ready = readyAudio(track, track.id in needed)
            val file = ready.file
            val neteaseKey = track.syncNeteaseId()?.let(ContentKey::netease)
            val fileKey = if (neteaseKey == null) file?.let { runCatching { ContentKey.of(it) }.getOrNull() } else null
            val keys = syncKeys(neteaseKey, track.contentKey, fileKey)
                ?: return@forEach
            val syncId = track.syncId ?: ContentKey.newSyncId()
            if (track.contentKey != keys.remember || track.syncId != syncId) {
                db.trackDao().setIdentity(track.id, keys.remember, syncId)
            }
            val item = SyncTrack(
                syncId = syncId,
                contentKey = keys.contentKey,
                aliasKeys = keys.aliasKeys,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                size = file?.length() ?: track.fileSize ?: 0L,
                ext = audioExt(file?.name, track.mimeType),
                hasFile = file != null,
                favorite = track.isFavorite,
                hasArt = ready.hasArt,
                hasLyric = lyricFile(track) != null,
                lyricOffsetMs = track.lyricOffsetMs,
                artUrl = if (ready.hasArt) null else coverUrl(track.artworkUri),
            )
            tracks += item
            idByTrack[track.id] = syncId
            if (file != null) files[syncId] = file
        }
        val playlists = db.playlistDao().getAll().filter { !it.isSystem }.map { playlist ->
            val syncId = playlist.syncId ?: ContentKey.newSyncId().also { db.playlistDao().setSyncId(playlist.id, it) }
            val coverFile = playlistCoverFile(playlist.coverUri)
            com.rabbeat.app.sync.SyncPlaylist(
                syncId = syncId,
                name = playlist.name,
                updatedAt = playlist.updatedAt,
                sortOrder = playlist.sortOrder,
                trackSyncIds = db.playlistDao().refs(playlist.id).mapNotNull { idByTrack[it.trackId] },
                hasCover = coverFile != null,
                coverUrl = if (coverFile != null) null else coverUrl(playlist.coverUri),
            )
        }
        val memberOrder = db.playlistDao().refs(LikedPlaylist.ID).mapNotNull { idByTrack[it.trackId] }
        val seen = memberOrder.toSet()
        val likes = memberOrder + tracks.filter { it.favorite && it.syncId !in seen }.map { it.syncId }
        val likesAt = db.playlistDao().get(LikedPlaylist.ID)?.updatedAt ?: 0L
        return Built(tracks, playlists, likes, likesAt, files)
    }

    private suspend fun storeReceived(file: File, meta: SyncTrack) {
        val existing = findReceived(meta)
        if (existing != null) {
            val have = readable(existing)
            if (have == null) {
                db.trackDao().updateUri(existing.id, file.toURI().toString())
                db.trackDao().updateOrigin(existing.id, if (existing.id.startsWith("ne_")) TrackOrigin.CACHED else TrackOrigin.LOCAL)
                db.trackDao().updateDownloadState(existing.id, DownloadState.DOWNLOADED)
            } else if (have.absolutePath != file.absolutePath) {
                file.delete()
            }
            if (existing.contentKey.isNullOrBlank() || existing.syncId.isNullOrBlank()) {
                db.trackDao().setIdentity(
                    existing.id,
                    existing.contentKey?.takeIf { it.isNotBlank() } ?: meta.contentKey,
                    existing.syncId?.takeIf { it.isNotBlank() } ?: meta.syncId,
                )
            }
            if (meta.favorite && !existing.isFavorite) db.trackDao().setFavorite(existing.id, true)
            rememberArtUrl(existing, meta.artUrl)
            return
        }
        val claimed = buildList {
            add(meta.contentKey)
            addAll(meta.aliasKeys)
        }
        if (claimed.none { it.startsWith("ne:") }) {
            val actual = runCatching { ContentKey.of(file) }.getOrNull()
            if (actual == null || actual !in claimed) {
                file.delete()
                error("内容和手机上的对不上")
            }
        }
        ensureLocalSource()
        val id = "dsk_" + ContentKey.sha1(file.absolutePath).take(24)
        val now = System.currentTimeMillis()
        val artistId = "dart_" + ContentKey.sha1(meta.artist).take(16)
        val albumId = "dalb_" + ContentKey.sha1("${meta.artist}|${meta.album}").take(16)
        db.trackDao().upsertAll(
            listOf(
                TrackEntity(
                    id = id,
                    sourceId = LibraryScanner.LOCAL_SOURCE_ID,
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
                    mimeType = audioExt(file.name, null).let { ext ->
                        when (ext) {
                            "flac" -> "audio/flac"
                            "ogg" -> "audio/ogg"
                            "wav" -> "audio/wav"
                            "m4a" -> "audio/mp4"
                            else -> "audio/mpeg"
                        }
                    },
                    bitrate = null,
                    sampleRate = null,
                    fileSize = file.length(),
                    uri = file.toURI().toString(),
                    artworkUri = coverUrl(meta.artUrl),
                    origin = TrackOrigin.LOCAL,
                    downloadState = DownloadState.NONE,
                    isFavorite = meta.favorite,
                    playCount = 0,
                    lastPlayedAt = null,
                    addedAt = now,
                    folder = "来自手机",
                    contentKey = meta.contentKey,
                    syncId = meta.syncId,
                ),
            ),
        )
        if (db.albumDao().getAll().none { it.id == albumId }) {
            db.albumDao().upsertAll(
                listOf(
                    AlbumEntity(
                        id = albumId,
                        sourceId = LibraryScanner.LOCAL_SOURCE_ID,
                        title = meta.album,
                        artist = meta.artist,
                        year = null,
                        artworkUri = coverUrl(meta.artUrl),
                        trackCount = 1,
                        addedAt = now,
                    ),
                ),
            )
        }
        if (db.artistDao().getAll().none { it.id == artistId }) {
            db.artistDao().upsertAll(
                listOf(
                    ArtistEntity(
                        id = artistId,
                        sourceId = LibraryScanner.LOCAL_SOURCE_ID,
                        name = meta.artist,
                        artworkUri = null,
                        albumCount = 1,
                        trackCount = 1,
                    ),
                ),
            )
        }
    }

    private suspend fun findReceived(meta: SyncTrack): TrackEntity? {
        db.trackDao().byContentKey(meta.contentKey)?.let { return it }
        meta.aliasKeys.forEach { key ->
            if (key.isNotBlank()) db.trackDao().byContentKey(key)?.let { return it }
        }
        val keys = listOf(meta.contentKey) + meta.aliasKeys
        for (key in keys) {
            if (!key.startsWith("ne:")) continue
            val songId = key.removePrefix("ne:")
            if (songId.isNotEmpty() && songId.all { it.isDigit() }) {
                db.trackDao().get("ne_$songId")?.let { return it }
            }
        }
        return db.trackDao().bySyncId(meta.syncId)
    }

    private suspend fun ensureLocalSource() {
        if (db.sourceDao().get(LibraryScanner.LOCAL_SOURCE_ID) != null) return
        val now = System.currentTimeMillis()
        db.sourceDao().upsert(
            SourceEntity(
                id = LibraryScanner.LOCAL_SOURCE_ID,
                type = SourceType.LOCAL,
                displayName = "本地文件",
                configJson = "{}",
                status = SourceStatus.CONNECTED,
                lastSyncAt = now,
                createdAt = now,
            ),
        )
    }

    private suspend fun applyRemoteArtUrls(tracks: List<SyncTrack>) {
        tracks.forEach { remote ->
            val local = db.trackDao().byContentKey(remote.contentKey) ?: return@forEach
            rememberArtUrl(local, remote.artUrl)
        }
    }

    private suspend fun rememberArtUrl(track: TrackEntity, raw: String?) {
        val url = coverUrl(raw) ?: return
        if (artFile(track) != null || !track.artworkUri.isNullOrBlank()) return
        db.trackDao().updateArtworkUri(track.id, url)
        val albumId = track.albumId
        if (albumId != null) {
            val album = db.albumDao().get(albumId)
            if (album != null && album.artworkUri.isNullOrBlank()) db.albumDao().updateArtworkUri(album.id, url)
        }
    }

    private suspend fun saveArt(syncId: String, bytes: ByteArray): Boolean {
        val track = db.trackDao().bySyncId(syncId) ?: return false
        if (artFile(track) != null) return true
        val ext = if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "png" else "jpg"
        val out = File(settings.artRoot(), "${track.id}.$ext")
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
        val uri = out.toURI().toString()
        db.trackDao().updateArtworkUri(track.id, uri)
        val albumId = track.albumId
        if (albumId != null) {
            val album = db.albumDao().get(albumId)
            if (album != null && album.artworkUri.isNullOrBlank()) db.albumDao().updateArtworkUri(album.id, uri)
        }
        return true
    }

    private suspend fun saveLyric(syncId: String, text: String, offset: Long): Boolean {
        val track = db.trackDao().bySyncId(syncId) ?: return false
        val cleaned = text.trim()
        if (cleaned.isNotEmpty() && lyricFile(track) == null) {
            val safe = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "track" }
            val out = File(settings.lyricRoot(), "$safe.lrc")
            out.parentFile?.mkdirs()
            out.writeText(cleaned, Charsets.UTF_8)
            db.trackDao().updateLyricUri(track.id, out.toURI().toString())
        }
        if (offset != 0L && track.lyricOffsetMs == 0L) db.trackDao().updateLyricOffset(track.id, offset)
        return true
    }

    private suspend fun savePlaylistCover(syncId: String, bytes: ByteArray): Boolean {
        val playlist = db.playlistDao().bySyncId(syncId) ?: return false
        val ext = if (bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "png" else "jpg"
        val out = File(settings.artRoot(), "plcover_${playlist.id}.$ext")
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)
        val previous = playlist.coverUri
        db.playlistDao().setCover(playlist.id, out.toURI().toString())
        if (previous != null && previous.startsWith("file:")) {
            val old = runCatching { File(URI(previous)) }.getOrNull()
            if (old != null && old.absoluteFile != out.absoluteFile &&
                old.absoluteFile.parentFile == settings.artRoot().absoluteFile
            ) {
                old.delete()
            }
        }
        return true
    }

    private fun playlistCoverFile(uri: String?): File? {
        if (uri == null || !uri.startsWith("file:")) return null
        val file = runCatching { File(URI(uri)) }.getOrNull() ?: return null
        return file.takeIf { it.isFile && it.length() in 32..MAX_ART.toLong() }
    }

    private fun artFile(track: TrackEntity): File? {
        val uri = track.artworkUri ?: return null
        if (!uri.startsWith("file:")) return null
        val file = runCatching { File(URI(uri)) }.getOrNull() ?: return null
        return file.takeIf { it.isFile && it.length() in 32..MAX_ART.toLong() }
    }

    private fun lyricFile(track: TrackEntity): File? {
        track.lyricUri?.takeIf { it.startsWith("file:") }?.let { uri ->
            val file = runCatching { File(URI(uri)) }.getOrNull()
            if (file != null && file.isFile && file.length() in 1..MAX_LYRIC.toLong()) return file
        }
        if (track.uri.startsWith("file:")) {
            val audio = runCatching { File(URI(track.uri)) }.getOrNull()
            val sidecar = audio?.parentFile?.let { File(it, audio.nameWithoutExtension + ".lrc") }
            if (sidecar != null && sidecar.isFile && sidecar.length() in 1..MAX_LYRIC.toLong()) return sidecar
        }
        val safe = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "track" }
        val stored = File(settings.lyricRoot(), "$safe.lrc")
        return stored.takeIf { it.isFile && it.length() in 1..MAX_LYRIC.toLong() }
    }

    /** Favorites and songs that sit in a user playlist. Encrypted files outside this set stay put. */
    private suspend fun likedOrListedIds(): Set<String> {
        val ids = db.trackDao().favoriteIds().toMutableSet()
        db.playlistDao().getAll().filter { !it.isSystem }.forEach { playlist ->
            db.playlistDao().refs(playlist.id).forEach { ids += it.trackId }
        }
        return ids
    }

    private data class Ready(val file: File?, val hasArt: Boolean)

    /**
     * Plain audio is sent as-is. ncm/qmc songs that are liked or in a playlist are
     * sent as the already-decrypted file the player uses, so the other side can play them.
     */
    private suspend fun readyAudio(track: TrackEntity, needed: Boolean): Ready {
        val plain = readable(track)
        if (plain != null) return Ready(plain, artFile(track) != null)
        if (!needed) return Ready(null, false)
        val source = encryptedSource(track) ?: return Ready(null, false)
        val opened = openEncrypted(source, track.id) ?: return Ready(null, false)
        return Ready(opened.file, saveEmbeddedCover(track, opened.cover))
    }

    private fun encryptedSource(track: TrackEntity): File? {
        if (!track.uri.startsWith("file:")) return null
        val file = runCatching { File(URI(track.uri)) }.getOrNull() ?: return null
        if (!file.isFile || file.length() <= 0L) return null
        return file.takeIf { it.extension.lowercase() in ENCRYPTED }
    }

    private data class Opened(val file: File, val cover: ByteArray?)

    private fun openEncrypted(source: File, cacheKey: String): Opened? = runCatching {
        val dir = File(settings.cacheRoot(), "decoded").apply { mkdirs() }
        when (source.extension.lowercase()) {
            "ncm" -> {
                val decoded = source.inputStream().buffered().use { NcmDecoderCore(dir).decode(it, cacheKey) }
                    ?: return@runCatching null
                Opened(decoded.file, decoded.tags?.cover)
            }
            else -> {
                val qmc = QmcDecoderCore(dir)
                val decoded = qmc.cached(cacheKey)
                    ?: source.inputStream().channel.use { qmc.decode(it, cacheKey) }
                    ?: return@runCatching null
                Opened(decoded.file, null)
            }
        }
    }.getOrNull()

    private suspend fun saveEmbeddedCover(track: TrackEntity, cover: ByteArray?): Boolean {
        if (artFile(track) != null) return true
        if (cover == null || cover.size !in 32..MAX_ART) return false
        val ext = if (cover.size >= 8 && cover[0] == 0x89.toByte() && cover[1] == 'P'.code.toByte()) "png" else "jpg"
        val out = File(settings.artRoot(), "${track.id}.$ext")
        out.parentFile?.mkdirs()
        out.writeBytes(cover)
        val uri = out.toURI().toString()
        db.trackDao().updateArtworkUri(track.id, uri)
        val albumId = track.albumId
        if (albumId != null) {
            val album = db.albumDao().get(albumId)
            if (album != null && album.artworkUri.isNullOrBlank()) db.albumDao().updateArtworkUri(album.id, uri)
        }
        return true
    }

    private fun readable(track: TrackEntity): File? {
        if (track.uri.startsWith("file:")) {
            val file = runCatching { File(URI(track.uri)) }.getOrNull()
            if (file != null && file.isFile && file.length() > 0L && file.extension.lowercase() !in ENCRYPTED) {
                return file
            }
        }
        val cached = File(settings.cacheRoot(), "${track.id}.audio")
        if (cached.isFile && cached.length() > 0L) return cached
        return null
    }

    private fun incomingDir(): File = File(settings.convertRoot(), "来自手机").apply { mkdirs() }

    private fun HttpExchange.reply(code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun <T> dbCall(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun lanAddresses(port: Int): List<String> {
        val found = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        for (nic in interfaces) {
            if (!nic.isUp || nic.isLoopback) continue
            for (address in nic.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress) {
                    address.hostAddress?.let { found += "$it:$port" }
                }
            }
        }
        return found.distinct()
    }

    private companion object {
        val ENCRYPTED = setOf("ncm", "mgg", "qmc", "mflac", "tkm", "ofl_en", "ofc")
        const val MAX_ART = 12 * 1024 * 1024
        const val MAX_LYRIC = 1024 * 1024
    }
}
