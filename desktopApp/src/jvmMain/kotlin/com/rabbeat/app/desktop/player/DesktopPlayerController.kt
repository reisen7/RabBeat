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
import java.io.File
import java.net.URI
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

    private val decodeDir = File(LibraryScanner.dataDir(), "decoded")
    private val ncm = NcmDecoderCore(decodeDir)
    private val qmc = QmcDecoderCore(decodeDir)

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
        scope.launch { restore() }
        scope.launch { watchProgress() }
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
        if (!wasPlaying) {
            publish(false)
            _state.value = _state.value.copy(positionMs = target)
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
        if (next !in queue.indices) { publish(_state.value.isPlaying); return }
        if (delta > 0 && shuffle) shuffleHistory.addLast(index)
        index = next
        startTrack(0L, play = play)
    }

    private fun startTrack(seekMs: Long, play: Boolean) {
        val track = queue.getOrNull(index) ?: return
        val gen = synchronized(lineLock) { ++generation }
        playJob?.cancel()
        closeLine()
        _loading.value = true
        playJob = scope.launch(Dispatchers.IO) {
            val prepared = prepareLine(track, seekMs)
            if (prepared == null) {
                onTrackFailed(gen)
                return@launch
            }
            if (gen != generation) {
                runCatching { prepared.second.close() }
                runCatching { prepared.first.close() }
                return@launch
            }
            synchronized(lineLock) {
                if (gen != generation) {
                    runCatching { prepared.second.close() }
                    runCatching { prepared.first.close() }
                    return@launch
                }
                stream = prepared.second
                line = prepared.first
            }
            basePositionMs = seekMs
            paused = !play
            error = null
            consecutiveFailures = 0
            applyGain()
            if (play) prepared.first.start() else prepared.first.stop()
            _loading.value = false
            publish(play)
            if (shuffle) shufflePlayed += index
            persist()
            val ended = pump(prepared.first, prepared.second, gen)
            closeLine()
            if (ended && gen == generation) onTrackEnded()
        }
    }

    /** A track that failed to decode keeps the error, then auto-skips until the cap. */
    private suspend fun onTrackFailed(gen: Int) {
        if (gen != generation) return
        consecutiveFailures++
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

    /** Returns the open line + decoded PCM stream, or null when undecodable. */
    private suspend fun prepareLine(track: Track, seekMs: Long): Pair<SourceDataLine, AudioInputStream>? = withContext(Dispatchers.IO) {
        runCatching {
            val source = resolveSource(track)
            val raw = AudioSystem.getAudioInputStream(source)
            val pcm = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f, 16, 2, 4, 44100f, false,
            )
            val decoded = AudioSystem.getAudioInputStream(pcm, raw)
            if (seekMs > 0L) skipFully(decoded, (seekMs / 1000.0 * pcm.frameRate).toLong() * pcm.frameSize)
            val l = AudioSystem.getSourceDataLine(pcm)
            l.open(pcm)
            l to decoded
        }.getOrElse {
            error = it.message
            null
        }
    }

    /** Remote tracks are fetched into ~/.rabbeat/cache on first play, then read locally. */
    private fun resolveSource(track: Track): File {
        if (!track.uri.startsWith("http")) return resolveFile(track)
        val dir = File(LibraryScanner.dataDir(), "cache").apply { mkdirs() }
        val cached = File(dir, "${track.id}.audio")
        if (cached.isFile && cached.length() > 0) return cached
        val tmp = File(dir, "${track.id}.part")
        runCatching {
            URI(track.uri).toURL().openStream().use { input ->
                tmp.outputStream().use { input.copyTo(it, 64 * 1024) }
            }
            if (!tmp.renameTo(cached)) tmp.copyTo(cached, overwrite = true)
        }.onFailure { tmp.delete() }
        return if (cached.isFile && cached.length() > 0) cached
        else throw java.io.IOException("下载失败: ${track.title}")
    }

    private fun resolveFile(track: Track): File {
        val f = File(URI(track.uri))
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
    private fun pump(l: SourceDataLine, inStream: AudioInputStream, gen: Int): Boolean {
        val buf = ByteArray(8192)
        while (true) {
            if (generation != gen) return false
            val n = runCatching { inStream.read(buf) }.getOrElse { return false }
            if (n < 0) return true
            var off = 0
            while (off < n) {
                if (generation != gen) return false
                off += try {
                    l.write(buf, off, n - off)
                } catch (_: Exception) {
                    return false
                }
            }
        }
    }

    private fun onTrackEnded() {
        val ended = queue.getOrNull(index)
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

    private fun skipFully(input: AudioInputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            remaining -= input.skip(remaining)
        }
    }

    @Serializable
    private data class StoredQueue(val ids: List<String>)
}
