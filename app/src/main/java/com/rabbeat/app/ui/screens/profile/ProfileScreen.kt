package com.rabbeat.app.ui.screens.profile

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.PlayRecord
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.PlaylistRepository
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.CreatePlaylistDialog
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GroupDivider
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.components.SectionHeader
import com.rabbeat.app.ui.components.SettingRow
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ArtistStat(
    val name: String,
    val plays: Int,
    val artworkUri: String?,
)

data class ProfilePage(
    val playlists: List<UserPlaylist>,
    val recent: List<PlayRecord>,
    val totalPlays: Int,
    val trackCount: Int,
    val weekPlays: Int,
    val topArtists: List<ArtistStat>,
    val currentTrackId: String?,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    music: MusicRepository,
    private val playlists: PlaylistRepository,
    val player: PlayerController,
    private val favorites: ToggleFavoriteUseCase,
    private val downloads: DownloadRepository,
) : ViewModel() {
    val page = combine(
        music.observeTracks(),
        playlists.observePlaylists(),
        music.observePlayHistory(),
        player.state,
    ) { tracks, sheets, history, playback ->
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val topArtists = tracks
            .filter { it.playCount > 0 }
            .groupBy { it.artist }
            .map { (name, songs) ->
                ArtistStat(
                    name = name,
                    plays = songs.sumOf { it.playCount },
                    artworkUri = songs.firstNotNullOfOrNull { it.artworkUri },
                )
            }
            .sortedByDescending { it.plays }
            .take(3)
        ScreenState.Ready(
            ProfilePage(
                playlists = sheets,
                recent = history.distinctBy { it.track.id }.take(6),
                totalPlays = tracks.sumOf { it.playCount },
                trackCount = tracks.size,
                weekPlays = history.count { it.playedAt >= weekStart },
                topArtists = topArtists,
                currentTrackId = playback.currentTrack?.id,
            ),
        ) as ScreenState<ProfilePage>
    }.catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun playNext(track: Track) = player.playNext(track)
    fun enqueue(track: Track) = player.enqueue(track)
    fun favorite(id: String) { viewModelScope.launch { favorites(id) } }
    fun download(id: String) { viewModelScope.launch { downloads.enqueue(listOf(id)) } }
    fun createPlaylist(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(playlists.create(name))
    }
}

@Composable
fun ProfileScreen(
    onMenu: () -> Unit,
    onPlaylist: (String) -> Unit,
    onPlaylists: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val state by vm.page.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    when (val current = state) {
        ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
        ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
        ScreenState.Empty -> EmptyState(stringResource(R.string.empty_library), Modifier.fillMaxSize())
        is ScreenState.Ready -> ProfileBody(
            page = current.value,
            onMenu = onMenu,
            onPlaylist = onPlaylist,
            onPlaylists = onPlaylists,
            onHistory = onHistory,
            onSettings = onSettings,
            onCreate = { creating = true },
            onPlay = vm::play,
            onFavorite = vm::favorite,
            onPlayNext = vm::playNext,
            onEnqueue = vm::enqueue,
            onDownload = vm::download,
        )
    }

    if (creating) {
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onConfirm = { name -> vm.createPlaylist(name) { id -> onPlaylist(id) } },
        )
    }
}

@Composable
private fun ProfileBody(
    page: ProfilePage,
    onMenu: () -> Unit,
    onPlaylist: (String) -> Unit,
    onPlaylists: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onCreate: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onFavorite: (String) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEnqueue: (Track) -> Unit,
    onDownload: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 26.dp)) {
        item {
            ScreenHeader(
                title = stringResource(R.string.profile_title),
                leading = {
                    HeaderIconButton(
                        icon = Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.cd_open_menu),
                        onClick = onMenu,
                    )
                },
                trailing = {
                    HeaderIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.nav_settings),
                        onClick = onSettings,
                    )
                },
            )
        }

        item { ProfileCard(page) }

        item {
            SectionHeader(
                title = stringResource(R.string.profile_section_playlists),
                actionLabel = stringResource(R.string.profile_manage_playlists),
                onAction = onPlaylists,
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "new") { NewPlaylistCard(onCreate) }
                items(page.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist) { onPlaylist(playlist.id) }
                }
            }
        }
        if (page.playlists.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.profile_playlist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                )
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.profile_section_stats),
                actionLabel = stringResource(R.string.profile_view_all),
                onAction = onHistory,
            )
        }
        item {
            Column {
                SettingRow(
                    icon = Icons.Filled.DateRange,
                    title = stringResource(R.string.profile_week_plays),
                    iconColor = Color(0xFFFF9500),
                    trailing = { ValueText(page.weekPlays) },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Filled.GraphicEq,
                    title = stringResource(R.string.profile_total_plays),
                    iconColor = Color(0xFFFA2D48),
                    trailing = { ValueText(page.totalPlays) },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Filled.LibraryMusic,
                    title = stringResource(R.string.profile_tracks_heard),
                    iconColor = Color(0xFF007AFF),
                    trailing = {
                        Text(
                            stringResource(R.string.profile_tracks_count, page.trackCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalRabBeatPalette.current.secondaryLabel,
                        )
                    },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.profile_section_artists)) }
        item {
            if (page.topArtists.isEmpty()) {
                Text(
                    stringResource(R.string.profile_artists_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                )
            } else {
                ArtistCard(page.topArtists)
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.profile_section_recent),
                actionLabel = stringResource(R.string.profile_view_all),
                onAction = onHistory,
            )
        }
        if (page.recent.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.profile_recent_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
                )
            }
        } else {
            val recentTracks = page.recent.map { it.track }
            itemsIndexed(page.recent, key = { _, record -> record.id }) { index, record ->
                TrackRow(
                    track = record.track,
                    onClick = { onPlay(recentTracks, index) },
                    onPlayNext = { onPlayNext(record.track) },
                    onEnqueue = { onEnqueue(record.track) },
                    onFavorite = { onFavorite(record.track.id) },
                    onDownload = { onDownload(record.track.id) },
                    playing = record.track.id == page.currentTrackId,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(page: ProfilePage) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            StatTile(stringResource(R.string.profile_stat_plays), page.totalPlays, Modifier.weight(1f))
            StatTile(stringResource(R.string.profile_stat_playlists), page.playlists.size, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val palette = LocalRabBeatPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$value",
            style = MaterialTheme.typography.headlineSmall,
            color = palette.label,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
        )
    }
}

@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    val accent = LocalAccentPalette.current
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LocalRabBeatPalette.current.fill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = accent.color, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.profile_playlist_new),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            stringResource(R.string.profile_playlist_hint_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PlaylistCard(playlist: UserPlaylist, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        PlaylistCover(playlist, 150.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            stringResource(R.string.profile_tracks_count, playlist.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ArtistCard(artists: List<ArtistStat>) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val max = artists.maxOf { it.plays }.coerceAtLeast(1)
    Column {
        artists.forEachIndexed { index, artist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (index == 0) accent.color else palette.secondaryLabel,
                    modifier = Modifier.width(22.dp),
                )
                Artwork(artist.artworkUri, artist.name, Modifier.size(42.dp), 21.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { artist.plays.toFloat() / max },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent.color,
                        trackColor = palette.fill,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.profile_plays_count, artist.plays),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.secondaryLabel,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 96.dp)
                    .height(0.5.dp)
                    .background(palette.separator),
            )
        }
    }
}

@Composable
private fun ValueText(value: Int) {
    Text(
        stringResource(R.string.profile_plays_count, value),
        style = MaterialTheme.typography.labelLarge,
        color = LocalAccentPalette.current.color,
        fontWeight = FontWeight.Bold,
    )
}
