package com.rabbeat.app.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.domain.model.RepeatMode
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.theme.LocalAccentPalette

@Composable
fun PlayerOverlay(
    state: PlaybackUiState,
    player: PlaybackController,
    onCollapse: () -> Unit,
    onVolume: ((Float) -> Unit)? = null,
    db: com.rabbeat.app.data.local.RabBeatDatabase? = null,
) {
    val track = state.currentTrack
    val accent = LocalAccentPalette.current
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1f) }
    val scope = rememberCoroutineScope()
    var lyricReload by remember { mutableStateOf(0) }
    val lyrics = remember(track?.id, showLyrics, lyricReload) {
        track?.let { LyricLoader.loadFor(it) } ?: emptyList()
    }
    val offsetMs = androidx.compose.runtime.produceState(0L, track?.id) {
        if (db != null && track != null) value = db.trackDao().get(track.id)?.lyricOffsetMs ?: 0L
    }.value
    val lyricIndex = LyricLoader.currentIndex(lyrics, state.positionMs + offsetMs)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起")
            }
            Spacer(Modifier.weight(1f))
            Text("正在播放", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { sleepMenu = true }) {
                    Icon(
                        Icons.Default.Bedtime,
                        contentDescription = "睡眠定时",
                        tint = if (state.sleepTimerRemainingMs != null) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    listOf(0L to "关闭", 15 * 60_000L to "15 分钟", 30 * 60_000L to "30 分钟", 45 * 60_000L to "45 分钟", 60 * 60_000L to "1 小时").forEach { (ms, label) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { player.setSleepTimer(ms, fade = true); sleepMenu = false },
                        )
                    }
                }
            }
            IconButton(onClick = { showLyrics = !showLyrics; if (showLyrics) showQueue = false; lyricReload++ }) {
                Icon(
                    Icons.Default.Lyrics,
                    contentDescription = "歌词",
                    tint = if (showLyrics) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showQueue = !showQueue; if (showQueue) showLyrics = false }) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "队列",
                    tint = if (showQueue) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showQueue) {
            QueueList(state, player, Modifier.weight(1f))
        } else if (showLyrics) {
            LyricsPane(
                lyrics = lyrics,
                lyricIndex = lyricIndex,
                offsetMs = offsetMs,
                accent = accent,
                hasTrack = track != null,
                onOffset = { delta ->
                    if (db != null && track != null) {
                        val next = offsetMs + delta
                        scope.launch { db.trackDao().updateLyricOffset(track.id, next) }
                    }
                },
                onFetch = {
                    if (track != null) scope.launch {
                        NeteaseClient.fetchLrc(track.title, track.artist)?.let { lrc ->
                            runCatching {
                                val audio = java.io.File(java.net.URI(track.uri))
                                java.io.File(audio.parentFile, audio.nameWithoutExtension + ".lrc").writeText(lrc)
                                lyricReload++
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Artwork(
                    artworkUri = track?.artworkUri,
                    title = track?.title ?: "?",
                    modifier = Modifier.size(280.dp),
                    cornerRadius = 20.dp,
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                track?.title ?: "未在播放",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.let { "${it.artist} · ${it.album}" }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (lyrics.isNotEmpty()) {
                Text(
                    if (lyricIndex >= 0) lyrics[lyricIndex].text else "♪",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Slider(
                value = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
                onValueChange = { if (state.durationMs > 0) player.seekTo((it * state.durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "随机",
                        tint = if (state.shuffle) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(onClick = { player.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.color)
                        .clickable { player.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accent.onColor,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                FilledTonalIconButton(onClick = { player.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "循环",
                        tint = if (state.repeatMode != RepeatMode.OFF) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onVolume != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "音量",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; onVolume(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text("${(volume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LyricsPane(
    lyrics: List<LyricLoader.LrcLine>,
    lyricIndex: Int,
    offsetMs: Long,
    accent: com.rabbeat.app.ui.theme.AccentPalette,
    hasTrack: Boolean,
    onOffset: (Long) -> Unit,
    onFetch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (lyrics.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (hasTrack) "暂无歌词" else "未在播放",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasTrack) {
                        androidx.compose.material3.TextButton(onClick = onFetch) {
                            Text("在线匹配歌词")
                        }
                    }
                }
            }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            androidx.compose.runtime.LaunchedEffect(lyricIndex) {
                if (lyricIndex >= 0) listState.animateScrollToItem((lyricIndex - 3).coerceAtLeast(0))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                itemsIndexed(lyrics) { i, line ->
                    Text(
                        line.text,
                        style = if (i == lyricIndex) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = if (i == lyricIndex) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (i == lyricIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(onClick = { onOffset(-500) }) { Text("-0.5s") }
                Text(
                    "偏移 ${"%.1f".format(offsetMs / 1000f)}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.TextButton(onClick = { onOffset(500) }) { Text("+0.5s") }
            }
        }
    }
}

@Composable
private fun QueueList(state: PlaybackUiState, player: PlaybackController, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(state.queue, key = { i, t -> "${t.id}_$i" }) { i, track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { player.playQueue(state.queue, i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    artworkUri = track.artworkUri,
                    title = track.title,
                    modifier = Modifier.size(38.dp),
                    cornerRadius = 8.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (i == state.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
