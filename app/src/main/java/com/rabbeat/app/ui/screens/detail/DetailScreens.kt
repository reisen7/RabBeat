package com.rabbeat.app.ui.screens.detail

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.Album
import com.rabbeat.app.domain.model.Artist
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.SourceRepository
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.AccentButton
import com.rabbeat.app.ui.components.AddToPlaylistDialog
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.GlassSurface
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.SectionHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AlbumPage(val album: Album, val tracks: List<Track>)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val music: MusicRepository,
    private val player: PlayerController,
    private val favorites: ToggleFavoriteUseCase,
    private val downloads: DownloadRepository,
) : ViewModel() {
    fun page(albumId: String) = combine(music.observeAlbum(albumId), music.observeAlbumTracks(albumId)) { album, tracks ->
        if (album == null) ScreenState.Empty else ScreenState.Ready(AlbumPage(album, tracks))
    }.catch { emit(ScreenState.Failed) }

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun favorite(id: String) { viewModelScope.launch { favorites(id) } }
    fun download(id: String) { viewModelScope.launch { downloads.enqueue(listOf(id)) } }
    fun hide(id: String) { viewModelScope.launch { music.hide(listOf(id)) } }
}

@Composable
fun AlbumDetailScreen(albumId: String, onBack: () -> Unit, vm: AlbumDetailViewModel = hiltViewModel()) {
    val page = remember(albumId) { vm.page(albumId) }
    val state by page.collectAsStateWithLifecycle(initialValue = ScreenState.Loading)
    var addTrackId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        DetailHeader("", onBack)
        when (val current = state) {
            ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
            ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
            ScreenState.Empty -> EmptyState(stringResource(R.string.detail_missing), Modifier.fillMaxSize())
            is ScreenState.Ready -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 30.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Artwork(
                            current.value.album.artworkUri,
                            current.value.album.title,
                            Modifier.size(240.dp).shadow(16.dp, RoundedCornerShape(10.dp), clip = false),
                            10.dp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            current.value.album.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            current.value.album.artist,
                            style = MaterialTheme.typography.titleLarge,
                            color = LocalAccentPalette.current.color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                R.string.album_year_tracks,
                                current.value.album.year?.toString() ?: stringResource(R.string.unknown_year),
                                current.value.tracks.size,
                            ),
                            color = LocalRabBeatPalette.current.secondaryLabel,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AccentButton(
                                text = stringResource(R.string.action_play),
                                onClick = { vm.play(current.value.tracks, 0) },
                                icon = Icons.Filled.PlayArrow,
                                enabled = current.value.tracks.isNotEmpty(),
                                tonal = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
                itemsIndexed(current.value.tracks) { index, track ->
                    TrackRow(
                        track,
                        { vm.play(current.value.tracks, index) },
                        { vm.play(listOf(track), 0) },
                        { vm.play(listOf(track), 0) },
                        { vm.favorite(track.id) },
                        { vm.download(track.id) },
                        onAddToPlaylist = { addTrackId = track.id },
                        onHide = { vm.hide(track.id) },
                    )
                }
            }
        }
    }
    addTrackId?.let { trackId -> AddToPlaylistDialog(trackId) { addTrackId = null } }
}

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val music: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {
    fun page(artistId: String) = combine(
        music.observeArtist(artistId),
        music.observeArtistTracks(artistId),
        music.observeAlbums(),
    ) { artist, tracks, albums ->
        if (artist == null) ScreenState.Empty else ScreenState.Ready(Triple(artist, tracks, albums.filter { it.artist == artist.name }))
    }.catch { emit(ScreenState.Failed) }

    fun play(tracks: List<Track>) = player.playQueue(tracks, 0)
}

@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBack: () -> Unit,
    onAlbum: (String) -> Unit,
    vm: ArtistDetailViewModel = hiltViewModel(),
) {
    val page = remember(artistId) { vm.page(artistId) }
    val state by page.collectAsStateWithLifecycle(initialValue = ScreenState.Loading)
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.library_artists), onBack)
        when (val current = state) {
            ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
            ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
            ScreenState.Empty -> EmptyState(stringResource(R.string.detail_missing), Modifier.fillMaxSize())
            is ScreenState.Ready -> {
                val (artist, tracks, albums) = current.value
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(artist.artworkUri, artist.name, Modifier.size(160.dp), 80.dp)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.headlineLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                stringResource(R.string.artist_meta, artist.albumCount, artist.trackCount),
                                color = LocalRabBeatPalette.current.secondaryLabel,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                Modifier.padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AccentButton(
                                    text = stringResource(R.string.action_play),
                                    onClick = { vm.play(tracks) },
                                    icon = Icons.Filled.PlayArrow,
                                    enabled = tracks.isNotEmpty(),
                                    tonal = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    item { SectionHeader(stringResource(R.string.group_albums)) }
                    items(albums, key = { it.id }) { album ->
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onAlbum(album.id) }
                                    .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(album.artworkUri, album.title, Modifier.size(54.dp), 6.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.album_meta, album.artist, album.trackCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalRabBeatPalette.current.secondaryLabel,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 86.dp)
                                    .height(0.5.dp)
                                    .background(LocalRabBeatPalette.current.separator),
                            )
                        }
                    }
                }
            }
        }
    }
}

@HiltViewModel
class SourceDetailViewModel @Inject constructor(
    private val sources: SourceRepository,
) : ViewModel() {
    fun source(id: String) = sources.observeSource(id).map { source ->
        when {
            source == null -> ScreenState.Empty
            source.status == SourceStatus.ERROR -> ScreenState.Failed
            else -> ScreenState.Ready(source)
        }
    }.catch { emit(ScreenState.Failed) }

    fun sync(id: String) { viewModelScope.launch { sources.syncSource(id) } }
    fun remove(id: String, onDone: () -> Unit) { viewModelScope.launch { sources.removeSource(id); onDone() } }
}

@Composable
fun SourceDetailScreen(sourceId: String, onBack: () -> Unit, vm: SourceDetailViewModel = hiltViewModel()) {
    val page = remember(sourceId) { vm.source(sourceId) }
    val state by page.collectAsStateWithLifecycle(initialValue = ScreenState.Loading)
    Column(Modifier.fillMaxSize()) {
        DetailHeader(stringResource(R.string.settings_sources), onBack)
        when (val current = state) {
            ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
            ScreenState.Empty -> EmptyState(stringResource(R.string.detail_missing), Modifier.fillMaxSize())
            ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { vm.sync(sourceId) }
            is ScreenState.Ready -> SourceBody(current.value, { vm.sync(sourceId) }, { vm.remove(sourceId, onBack) })
        }
    }
}

@Composable
private fun SourceBody(source: MusicSource, onSync: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(20.dp)) {
        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(source.displayName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    source.baseUrl.ifBlank { source.type.name },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(statusText(source.status), active = source.status == SourceStatus.CONNECTED)
                    GlassPill(source.type.name)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    source.lastSyncAt?.let {
                        stringResource(R.string.source_last_sync, java.text.SimpleDateFormat.getDateTimeInstance().format(it))
                    } ?: stringResource(R.string.source_never_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentButton(stringResource(R.string.action_sync), onSync, icon = Icons.Filled.Refresh, tonal = true)
            GlassPill(
                text = stringResource(R.string.action_remove),
                icon = Icons.Filled.DeleteOutline,
                onClick = onRemove,
            )
        }
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        )
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 14.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun statusText(status: SourceStatus): String = when (status) {
    SourceStatus.CONNECTED -> stringResource(R.string.source_connected)
    SourceStatus.DISCONNECTED -> stringResource(R.string.source_disconnected)
    SourceStatus.SYNCING -> stringResource(R.string.source_syncing)
    SourceStatus.ERROR -> stringResource(R.string.source_error)
}
