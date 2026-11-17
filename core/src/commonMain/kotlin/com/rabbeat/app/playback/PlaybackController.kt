package com.rabbeat.app.playback

import com.rabbeat.app.domain.model.RepeatMode
import com.rabbeat.app.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

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

interface PlaybackController {
    val state: StateFlow<PlaybackUiState>

    fun warmUp()
    fun playQueue(tracks: List<Track>, startIndex: Int)
    fun toggle()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun setSpeed(value: Float)
    fun setSleepTimer(durationMs: Long, fade: Boolean)
    fun playNext(track: Track)
    fun playNext(tracks: List<Track>)
    fun enqueue(track: Track)
    fun moveQueueItem(from: Int, to: Int)
    fun removeFromQueue(itemIndex: Int)
    fun clearUpcoming()
    fun dropTracks(ids: Set<String>)
}
