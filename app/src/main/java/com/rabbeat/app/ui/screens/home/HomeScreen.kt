package com.rabbeat.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.usecase.HomeContent
import com.rabbeat.app.domain.usecase.ObserveHomeUseCase
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.AccentButton
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassSurface
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.SectionHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.util.ArtworkColorCache
import com.rabbeat.app.util.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NowPlaying(val track: Track?, val playing: Boolean)

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHome: ObserveHomeUseCase,
    network: NetworkStatus,
    val player: PlayerController,
    val colors: ArtworkColorCache,
    private val favorites: ToggleFavoriteUseCase,
    private val downloads: com.rabbeat.app.domain.repository.DownloadRepository,
) : ViewModel() {
    val nowPlaying = player.state
        .map { NowPlaying(it.currentTrack, it.isPlaying) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlaying(null, false))
    val offline = network.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val content = observeHome()
        .combine(network.online) { home, online ->
            if (home.totalTracks == 0 && home.sources.isEmpty()) ScreenState.Empty
            else ScreenState.Ready(home to online)
        }
        .catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun favorite(id: String) { viewModelScope.launch { favorites(id) } }
    fun download(id: String) { viewModelScope.launch { downloads.enqueue(listOf(id)) } }
}

@Composable
fun HomeScreen(
    onMenu: () -> Unit,
    onOpenPlayer: () -> Unit,
    onImport: () -> Unit,
    onHistory: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.content.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    when (val current = state) {
        ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
        ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
        ScreenState.Empty -> EmptyState(
            stringResource(R.string.empty_library),
            Modifier.fillMaxSize(),
            stringResource(R.string.empty_library_action),
            onImport,
        )
        is ScreenState.Ready -> {
            val home = current.value.first
            HomeFeed(
                home = home,
                current = nowPlaying.track,
                playing = nowPlaying.playing,
                playback = vm.player.state,
                onMenu = onMenu,
                onOpenPlayer = onOpenPlayer,
                onToggle = vm.player::toggle,
                onHistory = onHistory,
                onPlay = { track ->
                    val index = home.top.indexOfFirst { it.id == track.id }
                    vm.play(home.top, index.coerceAtLeast(0))
                },
                onFavorite = vm::favorite,
                onDownload = vm::download,
                colors = vm.colors,
            )
        }
    }
}

@Composable
private fun HomeFeed(
    home: HomeContent,
    current: Track?,
    playing: Boolean,
    playback: StateFlow<PlaybackUiState>,
    onMenu: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    onHistory: () -> Unit,
    onPlay: (Track) -> Unit,
    onFavorite: (String) -> Unit,
    onDownload: (String) -> Unit,
    colors: ArtworkColorCache,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 26.dp)) {
        item {
            ScreenHeader(
                title = stringResource(R.string.home_title),
                leading = {
                    HeaderIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.cd_open_menu),
                        onClick = onMenu,
                    )
                },
                trailing = {
                    HeaderIconButton(
                        icon = Icons.Filled.History,
                        contentDescription = stringResource(R.string.home_history),
                        onClick = onHistory,
                    )
                },
            )
        }

        item {
            SectionHeader(stringResource(R.string.home_continue))
            if (current == null) {
                GlassSurface(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = onOpenPlayer,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.empty_player),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalRabBeatPalette.current.secondaryLabel,
                        )
                        Spacer(Modifier.height(14.dp))
                        AccentButton(
                            stringResource(R.string.nav_library),
                            onOpenPlayer,
                            icon = Icons.Filled.PlayArrow,
                            tonal = true,
                        )
                    }
                }
            } else {
                ContinueCard(current, playing, playback, onOpenPlayer, onToggle, colors)
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_recent), onAction = onHistory)
            if (home.recent.isEmpty()) {
                Text(
                    stringResource(R.string.home_recent_empty),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalRabBeatPalette.current.secondaryLabel,
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(home.recent, key = { it.id }) { track ->
                        Column(
                            Modifier
                                .width(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPlay(track) },
                        ) {
                            Artwork(
                                track.artworkUri,
                                track.title,
                                Modifier.size(150.dp),
                                8.dp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                track.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalRabBeatPalette.current.secondaryLabel,
                            )
                        }
                    }
                }
            }
        }

        if (home.top.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.library_tracks)) }
            items(home.top, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlay(track) },
                    onPlayNext = { onPlay(track) },
                    onEnqueue = { onPlay(track) },
                    onFavorite = { onFavorite(track.id) },
                    onDownload = { onDownload(track.id) },
                    playing = current?.id == track.id,
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    current: Track,
    playing: Boolean,
    playback: StateFlow<PlaybackUiState>,
    onOpenPlayer: () -> Unit,
    onToggle: () -> Unit,
    colors: ArtworkColorCache,
) {
    val accent = LocalAccentPalette.current
    var tint by remember(current.id) { mutableStateOf<Color?>(null) }
    LaunchedEffect(current.artworkUri) {
        tint = colors.dominant(current.artworkUri)
    }
    val card = tint ?: accent.color

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(card)
            .clickable(onClick = onOpenPlayer),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(current.artworkUri, current.title, Modifier.size(96.dp), 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
                            tint = card,
                        )
                    }
                }
            }
            ContinueProgress(playback)
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ContinueProgress(playback: StateFlow<PlaybackUiState>) {
    val state by playback.collectAsStateWithLifecycle()
    val progress = if (state.durationMs <= 0L) 0f else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
