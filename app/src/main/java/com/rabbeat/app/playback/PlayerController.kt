package com.rabbeat.app.playback

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.rabbeat.app.data.local.dao.PlaybackStateDao
import com.rabbeat.app.data.local.entity.PlaybackStateEntity
import com.rabbeat.app.di.ApplicationScope
import com.rabbeat.app.domain.model.RepeatMode
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.util.AppLogger
import com.rabbeat.app.util.MediaItemFactory
import com.rabbeat.app.util.Mp3DurationReader
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val sleepTimerRemainingMs: Long? = null,
    val speed: Float = 1f,
    val error: String? = null,
    val restoring: Boolean = true,
)

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val playbackStateDao: PlaybackStateDao,
    private val mediaItemFactory: MediaItemFactory,
    private val mp3Duration: Mp3DurationReader,
    private val mediaCache: Lazy<MediaCache>,
    private val cacheStatus: NeteaseCacheStatus,
    private val logger: AppLogger,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var queue: List<Track> = emptyList()
    private var index: Int = -1
    private var pendingSeek: Long = 0L
    private var repeatMode: RepeatMode = RepeatMode.OFF
    private var shuffle: Boolean = false
    private var speed: Float = 1f
    private var error: String? = null
    private var sleepRemaining: Long? = null
    private var sleepJob: Job? = null
    private var lastPersistAt = 0L
    private var lastCountedTrackId: String? = null
    private var applyGeneration = 0
    private var player: androidx.media3.exoplayer.ExoPlayer? = null
    var mediaSession: androidx.media3.session.MediaSession? = null
        private set

    private var initJob: Job? = null

    fun warmUp() {
        if (initJob != null) return
        initJob = scope.launch {
            val start = SystemClock.elapsedRealtime()
            // SimpleCache scans its directory on first touch; keep that off the main thread.
            val factory = withContext(Dispatchers.IO) { mediaCache.get().dataSourceFactory }
            logger.d("Player", "cache ready ${SystemClock.elapsedRealtime() - start}ms")
            if (player == null) {
                val exo = androidx.media3.exoplayer.ExoPlayer.Builder(context)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        true,
                    )
                    .setHandleAudioBecomingNoisy(true)
                    .setWakeMode(C.WAKE_MODE_LOCAL)
                    .setMediaSourceFactory(
                        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory),
                    )
                    .build()
                exo.addListener(listener)
                player = exo
                val openApp = Intent(context, com.rabbeat.app.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pending = android.app.PendingIntent.getActivity(
                    context,
                    0,
                    openApp,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
                mediaSession = androidx.media3.session.MediaSession.Builder(context, exo)
                    .setSessionActivity(pending)
                    .build()
                // Bind the session service without startForegroundService. Media3 calls
                // startForeground itself when playback actually starts. Doing it here while
                // the restored queue is paused crashes with ForegroundServiceDidNotStartInTimeException.
                context.startService(Intent(context, RabBeatPlaybackService::class.java))
                logger.d("Player", "built ${SystemClock.elapsedRealtime() - start}ms")
            }
            // Siblings, not children: callers join initJob to wait for the player.
            scope.launch { restore() }
            scope.launch { watchProgress() }
            scope.launch { refreshMetadata() }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        warmUp()
        queue = tracks
        index = startIndex.coerceIn(tracks.indices)
        pendingSeek = 0L
        error = null
        lastCountedTrackId = null
        applyToPlayer(play = true)
    }

    fun toggle() {
        val exo = player
        if (exo == null && initJob?.isActive == true) {
            scope.launch { initJob?.join(); toggle() }
            return
        }
        if (exo == null || exo.mediaItemCount == 0) {
            if (queue.isEmpty()) return
            warmUp()
            applyToPlayer(play = true)
            return
        }
        if (exo.isPlaying) {
            exo.pause()
            scope.launch { persist() }
        } else {
            exo.play()
        }
    }

    fun next() {
        if ((player?.mediaItemCount ?: 0) > 0) player?.seekToNext() else bump(1, play = false)
    }

    fun previous() {
        if ((player?.mediaItemCount ?: 0) > 0) player?.seekToPrevious() else bump(-1, play = false)
    }

    fun seekTo(positionMs: Long) {
        pendingSeek = positionMs.coerceAtLeast(0L)
        player?.seekTo(pendingSeek)
        publish(player?.isPlaying == true, pendingSeek)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        player?.shuffleModeEnabled = shuffle
        publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        player?.repeatMode = when (repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
    }

    fun setSpeed(value: Float) {
        speed = value
        player?.playbackParameters = PlaybackParameters(value)
        publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
    }

    fun setSleepTimer(durationMs: Long, fade: Boolean) {
        sleepJob?.cancel()
        if (durationMs <= 0L) {
            sleepRemaining = null
            player?.volume = 1f
            publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
            return
        }
        val deadline = SystemClock.elapsedRealtime() + durationMs
        sleepJob = scope.launch {
            while (isActive) {
                val remain = deadline - SystemClock.elapsedRealtime()
                if (remain <= 0L) {
                    player?.pause()
                    player?.volume = 1f
                    sleepRemaining = null
                    publish(false, player?.currentPosition ?: 0L)
                    persist()
                    break
                }
                if (fade && remain < 15_000L) {
                    player?.volume = (remain / 15_000f).coerceIn(0f, 1f)
                }
                sleepRemaining = remain
                publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
                delay(250)
            }
        }
    }

    fun playNext(track: Track) = playNext(listOf(track))

    fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        warmUp()
        val exo = player
        if (exo == null) {
            if (initJob?.isActive == true) scope.launch { initJob?.join(); playNext(tracks) }
            return
        }
        if (exo.mediaItemCount == 0) {
            if (queue.isEmpty()) {
                playQueue(tracks, 0)
                return
            }
            // Restored queue not applied yet — insert into the pending queue.
            queue = queue.toMutableList().also { it.addAll((index + 1).coerceAtMost(it.size), tracks) }
            publish(false, pendingSeek)
            return
        }
        val at = (exo.currentMediaItemIndex + 1).coerceAtMost(exo.mediaItemCount)
        exo.addMediaItems(at, tracks.map { mediaItemFactory.create(it) })
        queue = queue.toMutableList().also { it.addAll(at.coerceAtMost(it.size), tracks) }
        publish(exo.isPlaying, exo.currentPosition)
    }

    fun enqueue(track: Track) {
        warmUp()
        val exo = player
        if (exo == null) {
            if (initJob?.isActive == true) scope.launch { initJob?.join(); enqueue(track) }
            return
        }
        if (exo.mediaItemCount == 0) {
            queue = queue + track
            if (index < 0) index = 0
            publish(false, pendingSeek)
            return
        }
        exo.addMediaItem(mediaItemFactory.create(track))
        queue = queue + track
        publish(exo.isPlaying, exo.currentPosition)
    }

    fun moveQueueItem(from: Int, to: Int) {
        queue = QueueManager.move(queue, from, to)
        index = QueueManager.shiftedIndex(index, from, to)
        player?.moveMediaItem(from, to)
        publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
        scope.launch { persist() }
    }

    fun removeFromQueue(itemIndex: Int) {
        if (itemIndex !in queue.indices) return
        val removingCurrent = itemIndex == index
        queue = QueueManager.remove(queue, itemIndex)
        player?.removeMediaItem(itemIndex)
        index = when {
            queue.isEmpty() -> -1
            itemIndex < index -> index - 1
            removingCurrent -> index.coerceAtMost(queue.lastIndex)
            else -> index
        }
        publish(player?.isPlaying == true && queue.isNotEmpty(), player?.currentPosition ?: 0L)
        scope.launch { persist() }
    }

    fun clearUpcoming() {
        val current = queue.getOrNull(index)
        queue = listOfNotNull(current)
        index = if (current == null) -1 else 0
        player?.let { exo ->
            val currentIndex = exo.currentMediaItemIndex
            if (currentIndex < exo.mediaItemCount - 1) {
                exo.removeMediaItems(currentIndex + 1, exo.mediaItemCount)
            }
            if (currentIndex > 0) exo.removeMediaItems(0, currentIndex)
        }
        publish(player?.isPlaying == true, player?.currentPosition ?: 0L)
        scope.launch { persist() }
    }

    private fun applyToPlayer(play: Boolean) {
        val generation = ++applyGeneration
        val snapshot = queue.toList()
        val startIndex = index
        val seek = pendingSeek
        val countThisPlay = play
        scope.launch {
            initJob?.join()
            val exo = player ?: return@launch
            // Only the track being loaded is measured up front; scanning every mp3 in
            // the queue serially was the stall users saw before playback restored.
            val current = snapshot.getOrNull(startIndex)
            val currentOverride = withContext(Dispatchers.IO) { current?.let { measuredMp3(it) } }
            if (generation != applyGeneration) return@launch
            if (current != null && currentOverride != null &&
                kotlin.math.abs(currentOverride - current.durationMs) > 1_000L
            ) {
                musicRepository.updateDuration(current.id, currentOverride)
                queue = queue.map { if (it.id == current.id) it.copy(durationMs = currentOverride) else it }
            }
            exo.setMediaItems(
                snapshot.mapIndexed { i, track ->
                    mediaItemFactory.create(track, if (i == startIndex) currentOverride else null)
                },
                startIndex.coerceAtLeast(0),
                seek,
            )
            exo.shuffleModeEnabled = shuffle
            exo.repeatMode = when (repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
            exo.playbackParameters = PlaybackParameters(speed)
            exo.prepare()
            logger.d("Player", "applied ${snapshot.size} items play=$countThisPlay")
            if (play) {
                exo.play()
                if (countThisPlay) notePlay()
            } else {
                exo.pause()
            }
            publish(play, seek)
            persist()
            measureQueueDurations(snapshot, startIndex, generation)
        }
    }

    /**
     * Measures mp3 durations off the critical path, then applies corrections in one
     * batch: a single Room transaction (one observer emission) and a single
     * replace pass. Doing this per track used to fire hundreds of player events —
     * the media-notification storm in the logs — and O(n²) getMediaItemAt scans.
     */
    private suspend fun measureQueueDurations(snapshot: List<Track>, startIndex: Int, generation: Int) {
        val corrections = mutableMapOf<String, Long>()
        snapshot.forEachIndexed { i, track ->
            if (generation != applyGeneration) return
            if (i == startIndex) return@forEachIndexed
            val measured = withContext(Dispatchers.IO) { measuredMp3(track) } ?: return@forEachIndexed
            if (kotlin.math.abs(measured - track.durationMs) > 1_000L) {
                corrections[track.id] = measured
            }
        }
        if (generation != applyGeneration || corrections.isEmpty()) return
        logger.d("Player", "duration corrections: ${corrections.size}")
        musicRepository.updateDurations(corrections)
        queue = queue.map { t -> corrections[t.id]?.let { t.copy(durationMs = it) } ?: t }
        val exo = player ?: return
        val exoIndexById = (0 until exo.mediaItemCount)
            .associate { exo.getMediaItemAt(it).mediaId to it }
        val tracksById = snapshot.associateBy { it.id }
        val current = exo.currentMediaItemIndex
        corrections.forEach { (id, measured) ->
            val exoIndex = exoIndexById[id]
            val track = tracksById[id]
            if (exoIndex != null && track != null && exoIndex != current) {
                exo.replaceMediaItem(exoIndex, mediaItemFactory.create(track, measured))
            }
        }
    }

    private fun measuredMp3(track: com.rabbeat.app.domain.model.Track): Long? {
        val mime = track.mimeType?.lowercase().orEmpty()
        val mp3 = mime == "audio/mpeg" || mime == "audio/mp3" ||
            track.uri.endsWith(".mp3", ignoreCase = true)
        if (!mp3) return null
        return mp3Duration.readMillis(track.uri)
    }

    private fun bump(delta: Int, play: Boolean) {
        if (queue.isEmpty()) return
        index = (index + delta).coerceIn(queue.indices)
        pendingSeek = 0L
        if (play) applyToPlayer(play = true) else publish(false, 0L)
    }

    private suspend fun restore() {
        val saved = playbackStateDao.get() ?: run {
            _state.update { it.copy(restoring = false) }
            return
        }
        val raw = saved.queueJson
        if (raw.isNullOrBlank()) {
            _state.update { it.copy(restoring = false) }
            return
        }
        val ids = runCatching { json.decodeFromString<StoredQueue>(raw).ids }.getOrDefault(emptyList())
        val restored = musicRepository.getTracks(ids)
        if (restored.isEmpty()) {
            _state.update { it.copy(restoring = false) }
            return
        }
        queue = restored
        index = restored.indexOfFirst { it.id == saved.trackId }.let { if (it < 0) 0 else it }
        pendingSeek = saved.positionMs
        logger.d("Player", "restored ${restored.size} tracks index=$index seek=${pendingSeek}ms")
        // Show the restored track immediately instead of waiting for the player.
        publish(false, pendingSeek)
        // Media items are applied lazily on first play (toggle -> applyToPlayer):
        // a whole-queue setMediaItems burst on main was the last startup hitch
        // when users scrolled immediately after the feed appeared.
        _state.update { it.copy(restoring = false) }
    }

    private suspend fun watchProgress() {
        while (scope.isActive) {
            delay(500)
            val exo = player ?: continue
            if (!exo.isPlaying) continue
            publish(true, exo.currentPosition)
            val now = SystemClock.elapsedRealtime()
            if (now - lastPersistAt > 5_000L) {
                lastPersistAt = now
                persist()
            }
        }
    }

    private suspend fun refreshMetadata() {
        musicRepository.observeTracks().conflate().collect { latest ->
            if (queue.isEmpty()) return@collect
            val byId = latest.associateBy { it.id }
            queue = queue.map { byId[it.id] ?: it }
            publish(player?.isPlaying == true, player?.currentPosition ?: pendingSeek)
        }
    }

    private suspend fun persist() {
        val snapshot = StoredQueue(queue.map { it.id })
        playbackStateDao.upsert(
            PlaybackStateEntity(
                trackId = queue.getOrNull(index)?.id,
                positionMs = player?.currentPosition ?: pendingSeek,
                queueJson = json.encodeToString(snapshot),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        lastPersistAt = SystemClock.elapsedRealtime()
    }

    private fun publish(isPlaying: Boolean, position: Long) {
        val track = queue.getOrNull(index)
        val reported = player?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: track?.durationMs ?: 0L
        _state.value = PlaybackUiState(
            currentTrack = track,
            isPlaying = isPlaying,
            positionMs = position.coerceAtLeast(0L),
            durationMs = reported,
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

    private fun notePlay() {
        val exo = player ?: return
        if (!exo.playWhenReady) return
        val id = exo.currentMediaItem?.mediaId ?: return
        if (id == lastCountedTrackId) return
        lastCountedTrackId = id
        scope.launch { musicRepository.recordPlayback(id, exo.currentPosition) }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null) {
                val found = queue.indexOfFirst { it.id == mediaId }
                if (found >= 0) index = found
            }
            publish(player.isPlaying, player.currentPosition)
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                scope.launch { persist() }
            }
            if (player.playbackState == Player.STATE_ENDED) {
                scope.launch { cacheStatus.refresh() }
            }
            if (player.playWhenReady) notePlay()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            notePlay()
            scope.launch { cacheStatus.refresh() }
        }

        override fun onPlayerError(playbackException: PlaybackException) {
            error = playbackException.message
            logger.e("Player", "playback error", playbackException)
            publish(false, player?.currentPosition ?: 0L)
        }
    }

    @Serializable
    private data class StoredQueue(val ids: List<String>)
}
