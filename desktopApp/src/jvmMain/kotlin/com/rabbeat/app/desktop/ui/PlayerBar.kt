package com.rabbeat.app.desktop.ui
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.domain.model.RepeatMode
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.theme.LocalAccentPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerBar(
    db: RabBeatDatabase,
    player: DesktopPlayerController,
    state: PlaybackUiState,
    queueOpen: Boolean,
    nowPlayingOpen: Boolean,
    onToggleNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val track = state.currentTrack
    val hasTrack = track != null
    val scope = rememberCoroutineScope()
    val liked = LocalLikedPlaylist.current
    val likedRefs by db.playlistDao().observeRefs(LikedPlaylist.ID).collectAsState(initial = emptyList())
    val isLiked = track != null && likedRefs.any { it.trackId == track.id }
    val volume by player.volume.collectAsState()
    val muted by player.muted.collectAsState()
    val loading by player.loading.collectAsState()
    // Brief loads (<250ms, e.g. local seek) shouldn't flash the play button into a spinner.
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            kotlinx.coroutines.delay(250)
            showSpinner = true
        } else {
            showSpinner = false
        }
    }
    var previewMs by remember(track?.id) { mutableStateOf<Long?>(null) }
    var sleepMenu by remember { mutableStateOf(false) }
    val shownPosition = previewMs ?: state.positionMs

    // Fade the bar's own backdrop out as the full-height NowPlayingPage slides in beneath it,
    // so both share one continuous backdrop with zero seam or color pop.
    val bgAlpha by animateFloatAsState(
        targetValue = if (nowPlayingOpen) 0f else 1f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
    )
    Box(Modifier.fillMaxWidth().height(80.dp)) {
        if (bgAlpha > 0.01f) {
            Box(Modifier.fillMaxSize().alpha(bgAlpha).background(translucentSurface()))
        }
        Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f * bgAlpha.coerceAtLeast(0.75f))),
        )
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Left: artwork + title/artist + like
            Row(Modifier.weight(1f).widthIn(min = 280.dp), verticalAlignment = Alignment.CenterVertically) {
                val coverHover = remember { MutableInteractionSource() }
                val coverHovered by coverHover.collectIsHoveredAsState()
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .hoverable(coverHover, hasTrack)
                        .hand()
                        .clickable(enabled = hasTrack, onClick = onToggleNowPlaying),
                ) {
                    Artwork(track?.artworkUri, track?.title ?: "?", Modifier.fillMaxSize(), 8.dp)
                    if (coverHovered && hasTrack) {
                        Box(
                            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (nowPlayingOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.widthIn(max = 260.dp)) {
                    Text(
                        track?.title ?: "还没有在播放",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.hand().clickable(enabled = hasTrack, onClick = onToggleNowPlaying),
                    )
                    Text(
                        state.error ?: (track?.artist ?: ""),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                        color = if (state.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasTrack) {
                    IconButton(
                        onClick = { track?.let { t -> scope.launch { liked?.toggle(t.id) ?: db.trackDao().toggleFavorite(t.id) } } },
                        modifier = Modifier.size(36.dp).hand(),
                    ) {
                        Icon(
                            if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "喜欢",
                            tint = if (isLiked) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Middle: controls + seekbar
            Column(
                Modifier.widthIn(max = 640.dp).weight(1.4f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (hasTrack) 1f else 0.4f),
                ) {
                    IconButton(
                        onClick = { player.toggleShuffle() },
                        enabled = hasTrack,
                        modifier = Modifier.size(36.dp).hand(),
                    ) {
                        Icon(
                            Icons.Default.Shuffle, contentDescription = "随机",
                            tint = if (state.shuffle) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { player.previous() }, enabled = hasTrack, modifier = Modifier.size(36.dp).hand()) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.color)
                            .hand()
                            .clickable(enabled = hasTrack) { player.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showSpinner) {
                            CircularProgressIndicator(
                                color = accent.onColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = accent.onColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick = { player.next() }, enabled = hasTrack, modifier = Modifier.size(36.dp).hand()) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { player.cycleRepeat() }, enabled = hasTrack, modifier = Modifier.size(36.dp).hand()) {
                        Icon(
                            if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "循环",
                            tint = if (state.repeatMode != RepeatMode.OFF) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().alpha(if (hasTrack) 1f else 0.4f),
                ) {
                    Text(
                        formatMs(shownPosition),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 38.dp),
                    )
                    SeekBar(
                        fraction = if (state.durationMs > 0) (shownPosition.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
                        onSeek = { f ->
                            if (state.durationMs > 0) previewMs = (f * state.durationMs).toLong()
                        },
                        commitOnRelease = true,
                        enabled = hasTrack && state.durationMs > 0,
                        onSeekFinished = {
                            previewMs?.let { player.seekTo(it) }
                            previewMs = null
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(
                        formatMs(state.durationMs),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 38.dp),
                    )
                }
            }

            // Right: sleep / lyrics / volume / queue
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                Box {
                    IconButton(onClick = { sleepMenu = true }, modifier = Modifier.size(36.dp).hand()) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "睡眠定时",
                            tint = if (state.sleepTimerRemainingMs != null) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    val sleepMs = state.sleepTimerRemainingMs
                    if (sleepMs != null) {
                        Text(
                            "${sleepMs / 60_000}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent.color,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp),
                        )
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        listOf(
                            0L to "关闭", 15 * 60_000L to "15 分钟", 30 * 60_000L to "30 分钟",
                            45 * 60_000L to "45 分钟", 60 * 60_000L to "1 小时",
                        ).forEach { (ms, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { player.setSleepTimer(ms, fade = true); sleepMenu = false },
                            )
                        }
                    }
                }
                IconButton(onClick = onToggleNowPlaying, modifier = Modifier.size(36.dp).hand()) {
                    Icon(
                        Icons.Default.Lyrics,
                        contentDescription = "歌词",
                        tint = if (nowPlayingOpen) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.onPointerEvent(PointerEventType.Scroll) { e ->
                        val delta = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (delta != 0f) player.setVolume(volume - delta * 0.05f)
                    },
                ) {
                    IconButton(onClick = { player.toggleMute() }, modifier = Modifier.size(36.dp).hand()) {
                        Icon(
                            if (muted || volume == 0f) Icons.Default.VolumeOff
                            else if (volume < 0.5f) Icons.Default.VolumeDown
                            else Icons.Default.VolumeUp,
                            contentDescription = "音量",
                            tint = if (muted) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    SeekBar(
                        fraction = if (muted) 0f else volume,
                        onSeek = { player.setVolume(it) },
                        enabled = true,
                        modifier = Modifier.width(110.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onToggleQueue, modifier = Modifier.size(36.dp).hand()) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        tint = if (queueOpen) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        }
    }
}
