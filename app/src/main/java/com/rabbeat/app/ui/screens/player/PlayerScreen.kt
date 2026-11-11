package com.rabbeat.app.ui.screens.player

import android.widget.Toast

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.data.repository.LyricRepository
import com.rabbeat.app.domain.model.RepeatMode
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.util.ArtworkColorCache
import com.rabbeat.app.util.LyricSheet
import com.rabbeat.app.util.formatClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerController,
    settings: SettingsRepository,
    private val favorites: ToggleFavoriteUseCase,
    val colors: ArtworkColorCache,
    private val lyrics: LyricRepository,
    music: MusicRepository,
) : ViewModel() {
    val playback = player.state
    val catalog = music.observeTracks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fade = settings.sleepFade.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val defaultMinutes = settings.sleepTimerMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)
    fun favorite() {
        viewModelScope.launch { player.state.value.currentTrack?.let { favorites(it.id) } }
    }
    suspend fun loadLyrics(trackId: String) = lyrics.load(trackId)
    fun linkLyrics(trackId: String, sourceId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            lyrics.link(trackId, sourceId)
            onDone()
        }
    }
    fun unlinkLyrics(trackId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            lyrics.unlink(trackId)
            onDone()
        }
    }
}

@Composable
fun PlayerScreen(onBack: () -> Unit, onQueue: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val state by vm.playback.collectAsStateWithLifecycle()
    val track = state.currentTrack
    val context = LocalContext.current
    var tint by remember(track?.id) { mutableStateOf(Color(0xFF7C5CFF)) }
    LaunchedEffect(track?.artworkUri) {
        tint = vm.colors.dominant(track?.artworkUri) ?: Color(0xFF7C5CFF)
    }
    var lyrics by remember { mutableStateOf(false) }
    var sleep by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(false) }
    var lyricQuery by remember { mutableStateOf("") }
    var lyricScale by remember { mutableFloatStateOf(1f) }
    var lyricNonce by remember { mutableIntStateOf(0) }
    var sheet by remember(track?.id) { mutableStateOf(LyricSheet.EMPTY) }
    var lyricReady by remember(track?.id) { mutableStateOf(false) }
    LaunchedEffect(track?.id, lyricNonce) {
        val id = track?.id ?: return@LaunchedEffect
        lyricReady = false
        sheet = vm.loadLyrics(id)
        lyricReady = true
    }
    val lyricState = rememberLazyListState()
    val activeLine = if (sheet.synced) sheet.lines.indexOfLast { state.positionMs >= it.timeMs }.coerceAtLeast(0) else -1
    LaunchedEffect(activeLine, lyrics) {
        if (lyrics && activeLine >= 0 && !lyricState.isScrollInProgress) {
            lyricState.animateScrollToItem((activeLine - 2).coerceAtLeast(0))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tint.copy(alpha = 0.70f),
                            lerp(tint, Color.Black, 0.85f),
                        ),
                    ),
                ),
        )
        if (track != null) {
            Artwork(
                track.artworkUri,
                track.title,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.35f
                        scaleY = 1.35f
                    }
                    .blur(90.dp),
                0.dp,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                tint.copy(alpha = 0.55f),
                                lerp(tint, Color.Black, 0.85f),
                            ),
                        ),
                    ),
            )
        }

        when {
            state.restoring && track == null -> LoadingState(Modifier.fillMaxSize())
            track == null -> EmptyState(stringResource(R.string.empty_player), Modifier.fillMaxSize())
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                    )
                }

                if (lyrics) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            track.artworkUri,
                            track.title,
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { lyrics = false },
                            8.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.copyOnLongPress(track.title),
                            )
                            Text(
                                track.artist,
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.copyOnLongPress(track.artist),
                            )
                        }
                    }
                    LyricsView(
                        sheet = sheet,
                        ready = lyricReady,
                        activeLine = activeLine,
                        scale = lyricScale,
                        listState = lyricState,
                        onSeek = { vm.player.seekTo(it) },
                        modifier = Modifier.weight(1f).padding(top = 10.dp),
                    )
                } else {
                    val scale by animateFloatAsState(
                        targetValue = if (state.isPlaying) 1f else 0.82f,
                        animationSpec = spring(dampingRatio = 0.7f),
                        label = "art-scale",
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Artwork(
                            track.artworkUri,
                            track.title,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .shadow(24.dp, RoundedCornerShape(12.dp), clip = false)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) { lyrics = true },
                            12.dp,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.copyOnLongPress(track.title),
                        )
                        Text(
                            track.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.copyOnLongPress(track.artist),
                        )
                        state.error?.let {
                            Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                Toast.makeText(
                                    context,
                                    context.getString(if (track.isFavorite) R.string.liked_removed else R.string.liked_added),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                vm.favorite()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.player_favorite),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .clickable { moreMenu = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                contentDescription = stringResource(R.string.cd_more),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_add_playlist)) },
                                onClick = { moreMenu = false; addToPlaylist = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lyrics_link)) },
                                onClick = { moreMenu = false; linkOpen = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lyrics_size)) },
                                onClick = { moreMenu = false; lyricScale = if (lyricScale >= 1.4f) 1f else lyricScale + 0.2f },
                            )
                            if (sheet.linked) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_unlink)) },
                                    onClick = { moreMenu = false; vm.unlinkLyrics(track.id) { lyricNonce++ } },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_sleep)) },
                                onClick = { moreMenu = false; sleep = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.player_speed)) },
                                onClick = { moreMenu = false; speed = true },
                            )
                        }
                    }
                }

                var dragging by remember { mutableStateOf(false) }
                var drag by remember { mutableFloatStateOf(0f) }
                var barWidth by remember { mutableIntStateOf(1) }
                val shown = if (dragging) drag else state.positionMs.toFloat()
                val fraction = (shown / state.durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                val barHeight by animateDpAsState(if (dragging) 12.dp else 7.dp, label = "seek-h")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .onSizeChanged { barWidth = it.width.coerceAtLeast(1) }
                            .pointerInput(state.durationMs) {
                                detectTapGestures { offset ->
                                    val f = (offset.x / barWidth).coerceIn(0f, 1f)
                                    vm.player.seekTo((f * state.durationMs).toLong())
                                }
                            }
                            .pointerInput(state.durationMs) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        dragging = true
                                        drag = (offset.x / barWidth).coerceIn(0f, 1f) * state.durationMs
                                    },
                                    onDragEnd = {
                                        vm.player.seekTo(drag.toLong())
                                        dragging = false
                                    },
                                    onDragCancel = { dragging = false },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        drag = (drag + amount / barWidth * state.durationMs)
                                            .coerceIn(0f, state.durationMs.toFloat())
                                    },
                                )
                            },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(barHeight)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.75f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatClock(shown.toLong()),
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    )
                    Text(
                        formatClock(state.durationMs),
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                state.sleepTimerRemainingMs?.let {
                    Text(
                        stringResource(R.string.sleep_remaining, formatClock(it)),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_prev),
                        tint = Color.White,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = vm.player::previous,
                            ),
                    )
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                        tint = Color.White,
                        modifier = Modifier
                            .size(64.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = vm.player::toggle,
                            ),
                    )
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        tint = Color.White,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null,
                                onClick = vm.player::next,
                            ),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PlayerBottomToggle(
                        icon = Icons.Filled.Lyrics,
                        contentDescription = stringResource(R.string.player_lyrics),
                        active = lyrics,
                        onClick = { lyrics = !lyrics },
                    )
                    PlayerBottomToggle(
                        icon = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        active = state.shuffle,
                        onClick = vm.player::toggleShuffle,
                    )
                    PlayerBottomToggle(
                        icon = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.player_repeat),
                        active = state.repeatMode != RepeatMode.OFF,
                        onClick = vm.player::cycleRepeat,
                    )
                    PlayerBottomToggle(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.player_queue),
                        onClick = onQueue,
                    )
                }
            }
        }
    }
    if (sleep) SleepDialog(vm, onDismiss = { sleep = false })
    if (speed) SpeedDialog(state.speed, { vm.player.setSpeed(it); speed = false }, { speed = false })
    if (addToPlaylist && track != null) {
        com.rabbeat.app.ui.components.AddToPlaylistDialog(track.id) { addToPlaylist = false }
    }
    if (linkOpen && track != null) {
        val catalog by vm.catalog.collectAsStateWithLifecycle()
        val picks = catalog.filter {
            lyricQuery.isBlank() || it.title.contains(lyricQuery, true) || it.artist.contains(lyricQuery, true)
        }.take(40)
        AlertDialog(
            onDismissRequest = { linkOpen = false },
            title = { Text(stringResource(R.string.lyrics_pick)) },
            text = {
                Column {
                    OutlinedTextField(value = lyricQuery, onValueChange = { lyricQuery = it }, singleLine = true)
                    LazyColumn(Modifier.height(280.dp)) {
                        itemsIndexed(picks) { _, item ->
                            TextButton(onClick = {
                                vm.linkLyrics(track.id, item.id) {
                                    linkOpen = false
                                    lyricNonce++
                                }
                            }) { Text("${item.title} · ${item.artist}") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linkOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PlayerBottomToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) Color.White else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LyricsView(
    sheet: LyricSheet,
    ready: Boolean,
    activeLine: Int,
    scale: Float,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (sheet.linked) {
            Text(
                stringResource(R.string.lyrics_linked),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        when {
            !ready -> {
                LoadingState(Modifier.fillMaxWidth().weight(1f))
            }
            sheet.lines.isEmpty() -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.lyrics_empty),
                        color = Color.White.copy(alpha = 0.62f),
                    )
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                itemsIndexed(sheet.lines) { index, line ->
                    val active = sheet.synced && index == activeLine
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = sheet.synced) { onSeek(line.timeMs) }
                            .padding(vertical = 9.dp),
                    ) {
                        Text(
                            line.text,
                            color = if (!sheet.synced || active) Color.White else Color.White.copy(alpha = 0.35f),
                            fontSize = (if (active) 22 else 17).sp * scale,
                            style = if (active) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge,
                        )
                        line.translation?.let {
                            Text(it, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp * scale)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val fade by vm.fade.collectAsStateWithLifecycle()
    var fadeOn by remember(fade) { mutableStateOf(fade) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_sleep)) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    TextButton(onClick = {
                        vm.player.setSleepTimer(minutes * 60_000L, fadeOn)
                        onDismiss()
                    }) { Text(stringResource(R.string.sleep_minutes, minutes)) }
                }
                TextButton(onClick = { vm.player.setSleepTimer(0, false); onDismiss() }) { Text(stringResource(R.string.sleep_off)) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sleep_fade))
                    Switch(checked = fadeOn, onCheckedChange = { fadeOn = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SpeedDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_title)) },
        text = {
            Column {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    TextButton(onClick = { onPick(speed) }) {
                        Text(if (speed == current) "${speed}x ✓" else "${speed}x")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun Modifier.copyOnLongPress(text: String): Modifier {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return pointerInput(text) {
        detectTapGestures(onLongPress = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(text, text))
            if (android.os.Build.VERSION.SDK_INT < 33) {
                Toast.makeText(context, context.getString(R.string.copied, text), Toast.LENGTH_SHORT).show()
            }
        })
    }
}
