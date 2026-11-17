package com.rabbeat.app.desktop.player
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.PlaybackStateEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.domain.model.RepeatMode
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.playback.QueueManager
import com.rabbeat.app.util.NcmDecoderCore
import com.rabbeat.app.util.QmcDecoderCore
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * javax.sound-based [PlaybackController] for the desktop app. Decoded audio
 * always goes through a common PCM_SIGNED 44.1kHz/16-bit/stereo line, so
 * seeking is frame-accurate regardless of the source format. Encrypted
 * ncm/QMC-family files are decrypted through the shared core decoders first.
 */
class DesktopPlayerController(
    private val db: RabBeatDatabase,
    private val scope: CoroutineScope,
    private val settings: DesktopSettings = DesktopSettings(),
) : PlaybackController {

    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(20))
        .build()
    private val _state = MutableStateFlow(PlaybackUiState(restoring = false))
    override val state = _state.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _volume = MutableStateFlow(settings.volume.coerceIn(0f, 1f))
    val volume = _volume.asStateFlow()
    private val _muted = MutableStateFlow(settings.muted)
    val muted = _muted.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var index: Int = -1
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffle: Boolean = false
    private var speed: Float = 1f
    private var error: String? = null
    private var sleepRemaining: Long? = null
    private var sleepJob: Job? = null
    private var lastPersistAt = 0L

    private var decoderRoot: File? = null
    private var ncm: NcmDecoderCore? = null
    private var qmc: QmcDecoderCore? = null

    private fun decoders(): Pair<NcmDecoderCore, QmcDecoderCore> {
        val root = File(settings.cacheRoot(), "decoded")
        if (root != decoderRoot) {
            decoderRoot = root
            ncm = NcmDecoderCore(root)
            qmc = QmcDecoderCore(root)
        }
        return ncm!! to qmc!!
    }

    private val lineLock = Any()
    @Volatile private var line: SourceDataLine? = null
    @Volatile private var stream: AudioInputStream? = null
    private var playJob: Job? = null
    @Volatile private var paused = false
    @Volatile private var basePositionMs = 0L
    @Volatile private var generation = 0

    private var consecutiveFailures = 0
    private val shuffleHistory = ArrayDeque<Int>()
    private val shufflePlayed = mutableSetOf<Int>()
    private var prefetchFor: String? = null

    private var restored = false

    init {
        scope.launch {
            relocateCacheExtras(db, settings)
            restore()
        }
        scope.launch { watchProgress() }
        scope.launch(Dispatchers.IO) {
            val legacy = File(LibraryScanner.dataDir(), "decoded")
            if (legacy != File(settings.cacheRoot(), "decoded")) {
                runCatching { legacy.deleteRecursively() }
            }
        }
    }

    override fun warmUp() {
        // No engine to pre-build; state restore already runs at init.
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = startIndex.coerceIn(tracks.indices)
        shuffleHistory.clear()
        shufflePlayed.clear()
        startTrack(seekMs = 0L, play = true)
    }

    override fun toggle() {
        queue.getOrNull(index) ?: return
        val l = line
        if (l == null) {
            startTrack(seekMs = _state.value.positionMs, play = true)
            return
        }
        if (_state.value.isPlaying) {
            paused = true
            runCatching { l.stop() }
            publish(false)
            scope.launch { persist() }
        } else {
            paused = false
            runCatching { l.start() }
            publish(true)
        }
    }

    override fun next() = advance(1)
    override fun previous() {
        if (_state.value.positionMs > 3_000L) {
            seekTo(0L)
        } else if (shuffle && shuffleHistory.isNotEmpty()) {
            index = shuffleHistory.removeLast()
            startTrack(0L, play = true)
        } else {
            advance(-1)
        }
    }

    override fun seekTo(positionMs: Long) {
        val duration = queue.getOrNull(index)?.durationMs ?: 0L
        val target = positionMs.coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        val wasPlaying = _state.value.isPlaying || line != null
        basePositionMs = target
        _state.value = _state.value.copy(positionMs = target)
        if (!wasPlaying) {
            publish(false)
            return
        }
        startTrack(seekMs = target, play = !paused)
    }

    override fun toggleShuffle() {
        shuffle = !shuffle
        if (shuffle) {
            queue.getOrNull(index)?.let { shufflePlayed += index }
        } else {
            shuffleHistory.clear()
            shufflePlayed.clear()
        }
        publish(_state.value.isPlaying)
    }

    override fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        publish(_state.value.isPlaying)
    }

    override fun setSpeed(value: Float) {
        // javax.sound has no rate control; keep the value for UI parity.
        speed = value
        publish(_state.value.isPlaying)
    }

    override fun setSleepTimer(durationMs: Long, fade: Boolean) {
        sleepJob?.cancel()
        if (durationMs <= 0L) {
            sleepRemaining = null
            fadeFactor = 1f
            applyGain()
            publish(_state.value.isPlaying)
            return
        }
        val deadline = System.currentTimeMillis() + durationMs
        sleepJob = scope.launch {
            while (isActive) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0L) {
                    if (_state.value.isPlaying) toggle()
                    fadeFactor = 1f
                    applyGain()
                    sleepRemaining = null
                    publish(false)
                    persist()
                    break
                }
                fadeFactor = if (fade && remain < 15_000L) (remain / 15_000f).coerceIn(0f, 1f) else 1f
                applyGain()
                sleepRemaining = remain
                publish(_state.value.isPlaying)
                delay(250)
            }
        }
    }

    /** Desktop-only volume — not part of the shared interface (Android uses system volume). */
    fun setVolume(fraction: Float) {
        val v = fraction.coerceIn(0f, 1f)
        _volume.value = v
        settings.volume = v
        applyGain()
    }

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        settings.muted = m
        applyGain()
    }

    override fun playNext(track: Track) = playNext(listOf(track))

    override fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty() || index !in queue.indices) {
            playQueue(tracks, 0)
            return
        }
        queue = queue.toMutableList().also { it.addAll(index + 1, tracks) }
        publish(_state.value.isPlaying)
        scope.launch { persist() }
    }

    /** Point queued tracks at files that just moved with the convert directory. */
    fun remapUris(rewrite: (String) -> String) {
        var changed = false
        queue = queue.map { track ->
            val next = rewrite(track.uri)
            if (next == track.uri) {
                track
            } else {
                changed = true
                track.copy(uri = next)
            }
        }
        if (changed) publish(_state.value.isPlaying)
    }

    /** Point queued tracks at cover files that just moved with the cache directory. */
    fun remapArtwork(rewrite: (String?) -> String?) {
        var changed = false
        queue = queue.map { track ->
            val next = rewrite(track.artworkUri)
            if (next == track.artworkUri) {
                track
            } else {
                changed = true
                track.copy(artworkUri = next)
            }
        }
        if (changed) publish(_state.value.isPlaying)
    }

    override fun enqueue(track: Track) {
        queue = queue + track
        if (index < 0) {
            index = 0
            publish(false)
        }
        scope.launch { persist() }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        queue = QueueManager.move(queue, from, to)
        index = QueueManager.shiftedIndex(index, from, to)
        shuffleHistory.clear()
        shufflePlayed.clear()
        publish(_state.value.isPlaying)
        scope.launch { persist() }
    }

    override fun removeFromQueue(itemIndex: Int) {
        if (itemIndex !in queue.indices) return
        val removingCurrent = itemIndex == index
        queue = QueueManager.remove(queue, itemIndex)
        index = when {
            queue.isEmpty() -> -1
            itemIndex < index -> index - 1
            removingCurrent -> index.coerceAtMost(queue.lastIndex)
            else -> index
        }
        if (removingCurrent && _state.value.isPlaying) {
            startTrack(0L, play = true)
        } else {
            publish(_state.value.isPlaying && queue.isNotEmpty())
        }
        shuffleHistory.clear()
        shufflePlayed.clear()
        scope.launch { persist() }
    }

    /** Drops queued tracks that are about to leave the library. */
    override fun dropTracks(ids: Set<String>) {
        if (ids.isEmpty() || queue.none { it.id in ids }) return
        val removingCurrent = queue.getOrNull(index)?.id in ids
        val wasPlaying = _state.value.isPlaying
        queue = queue.filter { it.id !in ids }
        shuffleHistory.clear()
        shufflePlayed.clear()
        if (removingCurrent) {
            index = if (queue.isEmpty()) -1 else index.coerceIn(0, queue.lastIndex)
            if (queue.isEmpty()) {
                synchronized(lineLock) { generation++ }
                playJob?.cancel()
                closeLine()
                publish(false)
            } else {
                startTrack(0L, play = wasPlaying)
            }
        } else {
            if (index > queue.lastIndex) index = queue.lastIndex
            publish(wasPlaying && queue.isNotEmpty())
        }
        scope.launch { persist() }
    }

    override fun clearUpcoming() {
        val current = queue.getOrNull(index)
        queue = listOfNotNull(current)
        index = if (current == null) -1 else 0
        shuffleHistory.clear()
        shufflePlayed.clear()
        publish(_state.value.isPlaying)
        scope.launch { persist() }
    }

    // --- engine ---

    private fun advance(delta: Int, play: Boolean = true) {
        if (queue.isEmpty()) return
        val from = queue.getOrNull(index)
        val next = if (delta > 0 && shuffle && queue.size > 1) {
            val unplayed = queue.indices.filter { it != index && it !in shufflePlayed }
            when {
                unplayed.isNotEmpty() -> unplayed.random()
                repeatMode == RepeatMode.ALL -> {
                    shufflePlayed.clear()
                    (queue.indices - index).random()
                }
                else -> -1
            }
        } else {
            (index + delta).let { if (repeatMode == RepeatMode.ALL) Math.floorMod(it, queue.size) else it.coerceIn(queue.indices) }
        }
        if (next !in queue.indices) {
            DesktopLog.i("Player", "advance stop \"${from?.title}\" pos=${currentPositionMs()}ms delta=$delta")
            publish(_state.value.isPlaying)
            return
        }
        DesktopLog.i(
            "Player",
            "advance delta=$delta \"${from?.title}\"@${currentPositionMs()}ms -> \"${queue[next].title}\" dur=${queue[next].durationMs}ms",
        )
        if (delta > 0 && shuffle) shuffleHistory.addLast(index)
        index = next
        startTrack(0L, play = play)
    }

    private fun startTrack(seekMs: Long, play: Boolean) {
        val track = queue.getOrNull(index) ?: return
        val gen = synchronized(lineLock) { ++generation }
        DesktopLog.i("Player", "start \"${track.title}\" id=${track.id} dur=${track.durationMs}ms seek=$seekMs play=$play")
        playJob?.cancel()
        closeLine()
        basePositionMs = seekMs
        _state.value = _state.value.copy(positionMs = seekMs)
        _loading.value = true
        playJob = scope.launch(Dispatchers.IO) {
            val prepared = prepareLine(track, seekMs)
            if (prepared == null) {
                DesktopLog.e("Player", "prepare failed \"${track.title}\" err=$error")
                onTrackFailed(gen)
                return@launch
            }
            if (gen != generation) {
                runCatching { prepared.stream.close() }
                runCatching { prepared.line.close() }
                return@launch
            }
            synchronized(lineLock) {
                if (gen != generation) {
                    runCatching { prepared.stream.close() }
                    runCatching { prepared.line.close() }
                    return@launch
                }
                stream = prepared.stream
                line = prepared.line
            }
            basePositionMs = prepared.positionMs
            paused = !play
            error = null
            consecutiveFailures = 0
            applyGain()
            if (play) prepared.line.start() else prepared.line.stop()
            _loading.value = false
            publish(play)
            if (shuffle) shufflePlayed += index
            persist()
            var ended = pump(prepared.line, prepared.stream, gen, track)
            DesktopLog.i(
                "Player",
                "pump \"${track.title}\" ended=$ended pos=${currentPositionMs()}ms dur=${track.durationMs}ms live=${gen == generation}",
            )
            if (ended && gen == generation && prematureEnd()) {
                // Decoder hit EOF far before the tagged duration — corrupt data
                // or desync mid-file. Reopen, jump past the region, keep pumping
                // on the same line so playback continues when the tail is fine.
                DesktopLog.w("Player", "premature \"${track.title}\" pos=${currentPositionMs()}ms dur=${track.durationMs}ms")
                resyncStream(track)?.let { rs ->
                    synchronized(lineLock) {
                        runCatching { stream?.close() }
                        stream = rs
                    }
                    ended = pump(prepared.line, rs, gen, track)
                    DesktopLog.i(
                        "Player",
                        "resync pump \"${track.title}\" ended=$ended pos=${currentPositionMs()}ms dur=${track.durationMs}ms",
                    )
                }
            }
            closeLine()
            if (ended && gen == generation) {
                if (prematureEnd()) {
                    DesktopLog.w("Player", "give up \"${track.title}\" pos=${currentPositionMs()}ms dur=${track.durationMs}ms")
                    onTrackFailed(gen)
                } else {
                    onTrackEnded()
                }
            }
        }
    }

    /** Pump ended but the track is far from its tagged duration — decode stall, not a real end. */
    private fun prematureEnd(): Boolean {
        val dur = queue.getOrNull(index)?.durationMs ?: return false
        return dur > 0 && currentPositionMs() < dur - 5_000L
    }

    /** Reopens the source and skips ~256KB of compressed data to jump a stuck decoder past a corrupt region. */
    private fun resyncStream(track: Track): AudioInputStream? {
        val pos = currentPositionMs()
        return runCatching {
            val source = resolveSource(track)
            val raw = AudioSystem.getAudioInputStream(source)
            var left = 256L * 1024
            var skippedTotal = 0L
            while (left > 0) {
                val skipped = raw.skip(left)
                if (skipped <= 0) break
                left -= skipped
                skippedTotal += skipped
            }
            DesktopLog.w("Player", "resync \"${track.title}\" pos=${pos}ms skipped=$skippedTotal file=${source.length()}")
            val pcm = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false,
            )
            AudioSystem.getAudioInputStream(pcm, raw)
        }.getOrElse {
            DesktopLog.e("Player", "resync failed \"${track.title}\" pos=${pos}ms ${it.brief()}")
            null
        }
    }

    /** A track that failed to decode keeps the error, then auto-skips until the cap. */
    private suspend fun onTrackFailed(gen: Int) {
        if (gen != generation) return
        consecutiveFailures++
        DesktopLog.w(
            "Player",
            "failed \"${queue.getOrNull(index)?.title}\" pos=${currentPositionMs()}ms failures=$consecutiveFailures err=$error",
        )
        if (shuffle) shufflePlayed += index
        error = error ?: "无法解码 ${queue.getOrNull(index)?.title ?: ""}"
        _loading.value = false
        publish(false)
        if (consecutiveFailures >= minOf(queue.size, 5).coerceAtLeast(1)) return
        val hasNext = index < queue.lastIndex || (repeatMode == RepeatMode.ALL && queue.size > 1)
        if (!hasNext) return
        delay(1_000)
        if (gen == generation) advance(1)
    }

    private class OpenedAudio(
        val line: SourceDataLine,
        val stream: AudioInputStream,
        val positionMs: Long,
    )

    /** Returns the open line + decoded PCM stream, or null when undecodable. */
    private suspend fun prepareLine(track: Track, seekMs: Long): OpenedAudio? = withContext(Dispatchers.IO) {
        runCatching {
            val source = resolveSource(track)
            val fileFormat = runCatching { AudioSystem.getAudioFileFormat(source) }.getOrNull()
            DesktopLog.i(
                "Player",
                "open \"${track.title}\" file=${source.name} bytes=${source.length()} " +
                    "type=${fileFormat?.type} frames=${fileFormat?.frameLength} props=${fileFormat?.properties()}",
            )
            if (track.durationMs <= 0L) {
                // Tag-broken files import with duration 0 → no seek bar. The SPI
                // file-format "duration" property (µs) usually still knows it.
                runCatching {
                    (AudioSystem.getAudioFileFormat(source).properties()["duration"] as? Number)
                        ?.toLong()?.div(1000L)?.takeIf { it > 0 }
                }.getOrNull()?.let { ms ->
                    queue = queue.map { if (it.id == track.id) it.copy(durationMs = ms) else it }
                    db.trackDao().setDurationMs(track.id, ms)
                }
            }
            val mp3 = fileFormat?.type?.toString()?.contains("MP3", ignoreCase = true) == true ||
                source.extension.equals("mp3", ignoreCase = true)
            var positionMs = seekMs
            if (mp3 && fileLooksShort(source, track, fileFormat)) {
                val end = Mp3Seek.locate(source, Long.MAX_VALUE)
                if (end != null && end.endMs > 0 && track.durationMs > end.endMs + 2_000) {
                    DesktopLog.w(
                        "Player",
                        "short file \"${track.title}\" tag=${track.durationMs}ms audio=${end.endMs}ms bytes=${source.length()}",
                    )
                    queue = queue.map { if (it.id == track.id) it.copy(durationMs = end.endMs) else it }
                    db.trackDao().setDurationMs(track.id, end.endMs)
                    if (positionMs > end.endMs) positionMs = 0L
                }
            }
            val point = if (mp3 && positionMs > 0L) Mp3Seek.locate(source, positionMs) else null
            val positioned = if (point != null && point.reached && point.offset > 0L) {
                // FileInputStream has no mark(). jflac treats that as a hard failure and
                // AudioSystem stops before the MP3 reader gets a turn.
                val input = BufferedInputStream(source.inputStream(), 64 * 1024)
                if (skipRaw(input, point.offset)) {
                    DesktopLog.i("Player", "mp3 seek \"${track.title}\" ${positionMs}ms -> byte ${point.offset}")
                    AudioSystem.getAudioInputStream(input)
                } else {
                    input.close()
                    null
                }
            } else {
                null
            }
            val raw = positioned ?: AudioSystem.getAudioInputStream(source)
            val pcm = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f, 16, 2, 4, 44100f, false,
            )
            val decoded = AudioSystem.getAudioInputStream(pcm, raw)
            DesktopLog.i("Player", "decoded \"${track.title}\" format=${decoded.format} frames=${decoded.frameLength}")
            if (seekMs > 0L && positioned == null) {
                val pcmBytes = (seekMs / 1000.0 * pcm.frameRate).toLong() * pcm.frameSize
                if (mp3) skipByDecoding(decoded, pcmBytes) else skipFully(decoded, pcmBytes)
            }
            val l = AudioSystem.getSourceDataLine(pcm)
            l.open(pcm)
            OpenedAudio(l, decoded, positionMs)
        }.getOrElse {
            error = it.message
            DesktopLog.e("Player", "prepare \"${track.title}\" ${it.brief()}")
            null
        }
    }

    /** Remote tracks are fetched into ~/.rabbeat/cache on first play, then read locally. */
    private fun resolveSource(track: Track): File {
        if (!track.uri.startsWith("http")) return resolveFile(track)
        val dir = settings.cacheRoot().apply { mkdirs() }
        val cached = File(dir, "${track.id}.audio")
        if (cached.isFile && cached.length() == 0L) cached.delete()
        if (cached.isFile && cached.length() > 0) return cached
        val tmp = File(dir, "${track.id}.part")
        runCatching {
            downloadHttp(track.uri, tmp)
            if (!tmp.renameTo(cached)) tmp.copyTo(cached, overwrite = true)
        }.onFailure {
            tmp.delete()
            DesktopLog.e("Player", "download \"${track.title}\" ${it.message}")
        }
        return if (cached.isFile && cached.length() > 0) cached
        else throw java.io.IOException("下载失败: ${track.title}")
    }

    /**
     * NetEase's outer link answers 302 from https to an http CDN. Java's normal
     * redirect policy drops that hop and the body comes back empty.
     */
    private fun downloadHttp(url: String, dest: File) {
        var current = URI(url)
        repeat(5) {
            val request = HttpRequest.newBuilder(current)
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://music.163.com/")
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
            val code = response.statusCode()
            if (code in 300..399) {
                val location = response.headers().firstValue("Location").orElse(null)
                response.body().close()
                if (location.isNullOrBlank()) throw java.io.IOException("没有跳转地址 ($code)")
                current = current.resolve(location)
                return@repeat
            }
            if (code != 200) {
                response.body().close()
                throw java.io.IOException("HTTP $code")
            }
            val type = response.headers().firstValue("Content-Type").orElse("")
            if (type.startsWith("text/") || type.contains("html", ignoreCase = true)) {
                response.body().close()
                throw java.io.IOException("返回的不是音频 ($type)")
            }
            response.body().use { input ->
                dest.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
            if (dest.length() <= 0L) throw java.io.IOException("空文件")
            DesktopLog.i("Player", "downloaded ${dest.length()}B from ${current.host}")
            return
        }
        throw java.io.IOException("跳转过多")
    }

    private fun resolveFile(track: Track): File {
        val f = File(URI(track.uri))
        val (ncm, qmc) = decoders()
        return when (f.extension.lowercase()) {
            "ncm" -> f.inputStream().buffered().use { ncm.decode(it, track.id)?.file } ?: f
            "mgg", "qmc", "mflac", "tkm", "ofl_en", "ofc" ->
                qmc.cached(track.id)?.file
                    ?: f.inputStream().channel.use { qmc.decode(it, track.id)?.file }
                    ?: f
            else -> f
        }
    }

    /** Feeds decoded PCM into the line until EOF or cancellation. Returns true on natural EOF. */
    private fun pump(l: SourceDataLine, inStream: AudioInputStream, gen: Int, track: Track): Boolean {
        val buf = ByteArray(8192)
        var zeroReads = 0
        var bytes = 0L
        var nextMarkSec = basePositionMs / 1000L + 15L
        while (true) {
            if (generation != gen) {
                DesktopLog.i("Player", "pump cancel \"${track.title}\" pos=${currentPositionMs()}ms bytes=$bytes")
                return false
            }
            val n = try {
                inStream.read(buf)
            } catch (t: Throwable) {
                DesktopLog.e("Player", "read \"${track.title}\" pos=${currentPositionMs()}ms bytes=$bytes ${t.brief()}")
                return false
            }
            if (n < 0) {
                DesktopLog.w("Player", "eof \"${track.title}\" pos=${currentPositionMs()}ms bytes=$bytes dur=${track.durationMs}ms")
                return true
            }
            if (n == 0) {
                // Corrupt tail frames can make decode SPIs return 0 forever —
                // treat a sustained run of empty reads as EOF, else the track stalls.
                if (++zeroReads > 600) {
                    DesktopLog.w("Player", "zero-read \"${track.title}\" pos=${currentPositionMs()}ms bytes=$bytes dur=${track.durationMs}ms")
                    return true
                }
                Thread.sleep(2)
                continue
            }
            zeroReads = 0
            bytes += n
            val audioSec = basePositionMs / 1000L + bytes / 176_400L
            if (audioSec >= nextMarkSec) {
                DesktopLog.i("Player", "pos \"${track.title}\" audio=${audioSec}s line=${currentPositionMs()}ms bytes=$bytes")
                nextMarkSec = audioSec + 15L
            }
            var off = 0
            while (off < n) {
                if (generation != gen) {
                    DesktopLog.i("Player", "pump cancel \"${track.title}\" pos=${currentPositionMs()}ms bytes=$bytes")
                    return false
                }
                off += try {
                    l.write(buf, off, n - off)
                } catch (t: Exception) {
                    DesktopLog.e("Player", "write \"${track.title}\" pos=${currentPositionMs()}ms bytes=$bytes ${t.brief()}")
                    return false
                }
            }
        }
    }

    private fun onTrackEnded() {
        val ended = queue.getOrNull(index)
        DesktopLog.i("Player", "natural end \"${ended?.title}\" pos=${currentPositionMs()}ms dur=${ended?.durationMs}ms repeat=$repeatMode")
        if (ended != null) {
            scope.launch { db.trackDao().markPlayed(ended.id, System.currentTimeMillis()) }
        }
        when (repeatMode) {
            RepeatMode.ONE -> startTrack(0L, play = true)
            else -> {
                val hasNext = if (shuffle && queue.size > 1) {
                    queue.indices.any { it != index && it !in shufflePlayed } || repeatMode == RepeatMode.ALL
                } else {
                    index < queue.lastIndex || repeatMode == RepeatMode.ALL
                }
                if (hasNext) {
                    advance(1)
                } else {
                    publish(false)
                    _state.value = _state.value.copy(positionMs = 0L)
                    basePositionMs = 0L
                    scope.launch { persist() }
                }
            }
        }
    }

    /** Flush state and release the audio line before process exit. */
    fun shutdown() {
        scope.launch { persist() }.invokeOnCompletion { closeLine() }
    }

    private fun closeLine() {
        synchronized(lineLock) {
            runCatching { stream?.close() }
            runCatching { line?.close() }
            stream = null
            line = null
        }
    }

    private fun publish(playing: Boolean) {
        val track = queue.getOrNull(index)
        _state.value = PlaybackUiState(
            currentTrack = track,
            isPlaying = playing,
            positionMs = currentPositionMs(),
            durationMs = track?.durationMs ?: 0L,
            queue = queue,
            currentIndex = index,
            repeatMode = repeatMode,
            shuffle = shuffle,
            sleepTimerRemainingMs = sleepRemaining,
            speed = speed,
            error = error,
            restoring = false,
        )
    }

    private fun currentPositionMs(): Long {
        val l = line ?: return _state.value.positionMs
        return basePositionMs + (l.longFramePosition * 1000L / 44100L)
    }

    @Volatile private var fadeFactor = 1f

    private fun applyGain() = setGain(if (_muted.value) 0f else _volume.value * fadeFactor)

    private fun setGain(fraction: Float) {
        runCatching {
            val ctrl = line?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl ?: return
            val db = 20f * kotlin.math.log10(fraction.coerceAtLeast(0.0001f))
            ctrl.value = db.coerceIn(ctrl.minimum, ctrl.maximum)
        }
    }

    internal fun gainControl(): FloatControl? =
        line?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl

    private suspend fun watchProgress() {
        while (scope.isActive) {
            delay(200)
            if (_state.value.isPlaying) {
                _state.value = _state.value.copy(positionMs = currentPositionMs())
                val now = System.currentTimeMillis()
                if (now - lastPersistAt > 5_000L) {
                    lastPersistAt = now
                    persist()
                }
                maybePrefetchNext()
            }
        }
    }

    /** Once past half the track, warm the next track's source (cache/decrypt) on IO. */
    private fun maybePrefetchNext() {
        val current = queue.getOrNull(index) ?: return
        if (current.durationMs <= 0L || currentPositionMs() < current.durationMs / 2) return
        val candidate = peekNextIndex()?.let { queue.getOrNull(it) } ?: return
        if (prefetchFor == candidate.id) return
        prefetchFor = candidate.id
        scope.launch(Dispatchers.IO) { runCatching { resolveSource(candidate) } }
    }

    private fun peekNextIndex(): Int? {
        if (queue.isEmpty() || index !in queue.indices) return null
        if (repeatMode == RepeatMode.ONE) return index
        if (shuffle && queue.size > 1) {
            val unplayed = queue.indices.filter { it != index && it !in shufflePlayed }
            if (unplayed.isNotEmpty()) return unplayed.random()
            return if (repeatMode == RepeatMode.ALL) (queue.indices - index).random() else null
        }
        return if (index < queue.lastIndex) index + 1 else if (repeatMode == RepeatMode.ALL) 0 else null
    }

    private suspend fun persist() {
        lastPersistAt = System.currentTimeMillis()
        db.playbackStateDao().upsert(
            PlaybackStateEntity(
                trackId = queue.getOrNull(index)?.id,
                positionMs = currentPositionMs(),
                queueJson = json.encodeToString(StoredQueue(queue.map { it.id })),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun restore() {
        if (restored) return
        restored = true
        val saved = db.playbackStateDao().get() ?: return
        val raw = saved.queueJson ?: return
        val ids = runCatching { json.decodeFromString(StoredQueue.serializer(), raw).ids }.getOrDefault(emptyList())
        if (ids.isEmpty()) return
        val entities = db.trackDao().getByIds(ids)
        val byId = entities.associateBy { it.id }
        queue = ids.mapNotNull { byId[it]?.toDomain(null) }
        index = queue.indexOfFirst { it.id == saved.trackId }.let { if (it < 0) 0 else it }
        basePositionMs = saved.positionMs
        _state.value = _state.value.copy(positionMs = saved.positionMs)
        publish(false)
    }

    private fun Throwable.brief(): String {
        val top = stackTrace.take(6).joinToString(" | ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }
        return "${javaClass.simpleName}: $message [$top]"
    }

    /** Tag duration claims far more audio than this file can hold at its nominal bitrate. */
    private fun fileLooksShort(source: File, track: Track, fileFormat: javax.sound.sampled.AudioFileFormat?): Boolean {
        val bps = (fileFormat?.properties()?.get("mp3.bitrate.nominal.bps") as? Number)?.toLong() ?: return false
        if (bps <= 0L || track.durationMs <= 0L) return false
        val expected = track.durationMs * bps / 8_000
        return source.length() < expected * 85 / 100
    }

    private fun skipRaw(input: InputStream, bytes: Long): Boolean {
        var remaining = bytes
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) return false
            remaining -= n
        }
        return true
    }

    /** Discard already-decoded PCM. Used when an MP3 frame walk could not position the file. */
    private fun skipByDecoding(input: AudioInputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(64 * 1024)
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) {
                DesktopLog.w("Player", "seek decode short by ${remaining}B of $bytes")
                return
            }
            remaining -= n
        }
    }

    private fun skipFully(input: AudioInputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            // Decoded streams (mp3spi/jflac/vorbisspi) often report skip() == 0;
            // fall back to decoding and discarding so we never spin forever.
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n <= 0) return
            remaining -= n
        }
    }

    @Serializable
    private data class StoredQueue(val ids: List<String>)
}
