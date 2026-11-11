package com.rabbeat.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.SearchResult
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.AddToPlaylistDialog
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.SectionHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val music: MusicRepository,
    private val settings: SettingsRepository,
    private val player: PlayerController,
    private val favorites: ToggleFavoriteUseCase,
    private val downloads: DownloadRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val recent = settings.recentSearches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val results = query.debounce(250).flatMapLatest { raw ->
        val q = raw.trim()
        if (q.isEmpty()) flowOf(ScreenState.Empty)
        else music.search(q).map { result ->
            if (result.isEmpty) ScreenState.Empty else ScreenState.Ready(result)
        }
    }.catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun update(value: String) { query.value = value }
    fun submit() {
        viewModelScope.launch { settings.pushRecentSearch(query.value) }
    }
    fun clearRecent() { viewModelScope.launch { settings.clearRecentSearches() } }
    fun play(result: SearchResult, index: Int) = player.playQueue(result.tracks, index)
    fun favorite(id: String) { viewModelScope.launch { favorites(id) } }
    fun download(id: String) { viewModelScope.launch { downloads.enqueue(listOf(id)) } }
    fun playNext(track: com.rabbeat.app.domain.model.Track) = player.playNext(track)
    fun enqueue(track: com.rabbeat.app.domain.model.Track) = player.enqueue(track)
    fun hide(id: String) { viewModelScope.launch { music.hide(listOf(id)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onMenu: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val state by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var addTrackId by remember { mutableStateOf<String?>(null) }
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_search),
            leading = {
                HeaderIconButton(
                    icon = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_menu),
                    onClick = onMenu,
                )
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.fill)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = palette.secondaryLabel,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = query,
                onValueChange = vm::update,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent.color),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submit() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.secondaryLabel,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { vm.update("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = palette.secondaryLabel,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        when {
            query.isBlank() -> {
                if (recent.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SearchHint()
                } else {
                    SectionHeader(
                        stringResource(R.string.search_recent),
                        actionLabel = stringResource(R.string.search_clear),
                        onAction = vm::clearRecent,
                    )
                    FlowRow(
                        Modifier.padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recent.forEach { chip ->
                            GlassPill(
                                text = chip,
                                icon = Icons.Filled.Search,
                                onClick = { vm.update(chip); vm.submit() },
                            )
                        }
                    }
                }
            }
            state is ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
            state is ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { vm.update(query) }
            state is ScreenState.Empty -> EmptyState(stringResource(R.string.empty_search, query), Modifier.fillMaxSize())
            state is ScreenState.Ready -> {
                val result = (state as ScreenState.Ready<SearchResult>).value
                LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                    if (result.tracks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                stringResource(R.string.group_tracks),
                                actionLabel = stringResource(R.string.smart_count, result.tracks.size),
                            )
                        }
                        items(result.tracks, key = { it.id }) { track ->
                            TrackRow(
                                track,
                                onClick = { vm.play(result, result.tracks.indexOf(track)); vm.submit() },
                                onPlayNext = { vm.playNext(track) },
                                onEnqueue = { vm.enqueue(track) },
                                onFavorite = { vm.favorite(track.id) },
                                onDownload = { vm.download(track.id) },
                                onAddToPlaylist = { addTrackId = track.id },
                                onHide = { vm.hide(track.id) },
                            )
                        }
                    }
                    if (result.albums.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.group_albums)) }
                        items(result.albums, key = { it.id }) { album ->
                            SearchRow(
                                onClick = { onAlbum(album.id); vm.submit() },
                            ) {
                                Artwork(album.artworkUri, album.title, Modifier.size(50.dp), 8.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.album_meta, album.artist, album.trackCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.secondaryLabel,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    if (result.artists.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.group_artists)) }
                        items(result.artists, key = { it.id }) { artist ->
                            SearchRow(
                                onClick = { onArtist(artist.id); vm.submit() },
                            ) {
                                Artwork(artist.artworkUri, artist.name, Modifier.size(50.dp), 25.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(
                                        stringResource(R.string.artist_meta, artist.albumCount, artist.trackCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = palette.secondaryLabel,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    addTrackId?.let { trackId -> AddToPlaylistDialog(trackId) { addTrackId = null } }
}

@Composable
private fun SearchRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val palette = LocalRabBeatPalette.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 82.dp)
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}

@Composable
private fun SearchHint() {
    val palette = LocalRabBeatPalette.current
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ManageSearch,
            contentDescription = null,
            tint = palette.tertiaryLabel,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(14.dp))
        Text(
            stringResource(R.string.search_hint_body),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.secondaryLabel,
        )
    }
}
