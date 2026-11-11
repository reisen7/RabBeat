package com.rabbeat.app.ui.screens.library

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.data.local.LocalMediaScanner
import com.rabbeat.app.domain.model.Album
import com.rabbeat.app.domain.model.FolderSummary
import com.rabbeat.app.domain.model.Artist
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.SourceRepository
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.AddToPlaylistDialog
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.LocateButton
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.TrackRowPlaceholder
import com.rabbeat.app.ui.components.PillTabRow
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.SectionHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.util.NetworkStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab { Tracks, Albums, Artists, Folders }

data class LibraryControls(
    val tab: LibraryTab = LibraryTab.Tracks,
    val sort: TrackSort = TrackSort.TITLE,
    val offlineOnly: Boolean = false,
    val sourceId: String? = null,
)

data class LibraryBundle(
    val sources: List<MusicSource>,
    val controls: LibraryControls,
    val deviceOnline: Boolean,
    val currentTrackId: String?,
    val trackCount: Int,
    val albumCount: Int,
    val artistCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val music: MusicRepository,
    sources: SourceRepository,
    network: NetworkStatus,
    private val player: PlayerController,
    private val favorites: ToggleFavoriteUseCase,
    private val downloads: DownloadRepository,
    private val scanner: LocalMediaScanner,
) : ViewModel() {
    private val controls = MutableStateFlow(LibraryControls())
    private val query = combine(controls, network.online) { control, online ->
        TrackQuery(
            sort = control.sort,
            sourceId = control.sourceId,
            offlineOnly = control.offlineOnly || !online,
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, TrackQuery())

    val tracks = query.flatMapLatest { music.pageTracks(it) }.cachedIn(viewModelScope)
    val albums = music.pageAlbums().cachedIn(viewModelScope)
    val artists = music.pageArtists().cachedIn(viewModelScope)
    val folders = music.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val openFolder = MutableStateFlow<String?>(null)
    val folderTracks = openFolder.filterNotNull()
        .flatMapLatest { music.pageTracks(TrackQuery(folderPath = it)) }
        .cachedIn(viewModelScope)
    fun openFolder(path: String) { openFolder.value = path }

    private val currentTrackId = player.state
        .map { it.currentTrack?.id }
        .distinctUntilChanged()

    private val counts = combine(
        music.observeTrackCount(),
        music.observeAlbumCount(),
        music.observeArtistCount(),
    ) { tracks, albums, artists -> Triple(tracks, albums, artists) }

    private val misc = combine(
        controls,
        sources.observeSources(),
        network.online,
        currentTrackId,
    ) { control, sourceList, online, currentId ->
        LibraryBundle(sourceList, control, online, currentId, 0, 0, 0)
    }

    val ui = combine(misc, counts) { bundle, (tracks, albums, artists) ->
        val ready = bundle.copy(trackCount = tracks, albumCount = albums, artistCount = artists)
        if (tracks == 0 && albums == 0) ScreenState.Empty else ScreenState.Ready(ready)
    }.catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun setTab(tab: LibraryTab) = controls.update { it.copy(tab = tab) }
    fun setSort(sort: TrackSort) = controls.update { it.copy(sort = sort) }
    fun setOffline(value: Boolean) = controls.update { it.copy(offlineOnly = value) }
    fun setSource(id: String?) = controls.update { it.copy(sourceId = id) }
    fun play(index: Int) {
        viewModelScope.launch {
            player.playQueue(music.queueFrom(query.value, index), 0)
        }
    }
    fun playInFolder(path: String, index: Int) {
        viewModelScope.launch {
            player.playQueue(music.queueFrom(TrackQuery(folderPath = path), index), 0)
        }
    }
    fun locateCurrent(onFound: (Int) -> Unit) {
        viewModelScope.launch {
            val id = currentTrackId.first() ?: return@launch
            val index = music.trackIds(query.value).indexOf(id)
            if (index >= 0) onFound(index)
        }
    }
    fun playNext(track: Track) = player.playNext(track)
    fun enqueue(track: Track) = player.enqueue(track)
    fun favorite(id: String) { viewModelScope.launch { favorites(id) } }
    fun download(id: String) { viewModelScope.launch { downloads.enqueue(listOf(id)) } }
    fun hide(id: String) { viewModelScope.launch { music.hide(listOf(id)) } }
    fun hideFolder(path: String) {
        viewModelScope.launch {
            music.hide(music.trackIds(TrackQuery(folderPath = path, folderRecursive = true)))
        }
    }
    fun importDevice(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            if (!scanner.hasAudioPermission()) {
                onDone(-1)
                return@launch
            }
            onDone(scanner.importDeviceLibrary().trackCount)
        }
    }
}

@Composable
fun LibraryScreen(
    onMenu: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    when (val current = state) {
        ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
        ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
        ScreenState.Empty -> EmptyLibrary(vm)
        is ScreenState.Ready -> LibraryBody(current.value, onMenu, onAlbum, onArtist, vm)
    }
}

@Composable
private fun EmptyLibrary(vm: LibraryViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    val denied = stringResource(R.string.permission_denied)
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            scope.launch { snack.showSnackbar(denied) }
            return@rememberLauncherForActivityResult
        }
        vm.importDevice { count ->
            if (count <= 0) {
                scope.launch { snack.showSnackbar(context.getString(R.string.scan_empty)) }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        EmptyState(
            stringResource(R.string.empty_library),
            Modifier.fillMaxSize(),
            stringResource(R.string.settings_scan_device),
            onAction = { launcher.launch(permission) },
        )
        androidx.compose.material3.SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryBody(
    bundle: LibraryBundle,
    onMenu: () -> Unit,
    onAlbum: (String) -> Unit,
    onArtist: (String) -> Unit,
    vm: LibraryViewModel,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var addTrackId by remember { mutableStateOf<String?>(null) }
    val trackItems = vm.tracks.collectAsLazyPagingItems()
    val albumItems = vm.albums.collectAsLazyPagingItems()
    val artistItems = vm.artists.collectAsLazyPagingItems()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val artistListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val accent = LocalAccentPalette.current

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.nav_library),
            subtitle = stringResource(
                R.string.library_summary,
                bundle.trackCount,
                bundle.albumCount,
                bundle.artistCount,
            ),
            leading = {
                HeaderIconButton(
                    icon = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_menu),
                    onClick = onMenu,
                )
            },
            trailing = {
                Box {
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(R.string.sort),
                        onClick = { sortMenu = true },
                        active = bundle.controls.sort != TrackSort.TITLE,
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_title)) }, onClick = { vm.setSort(TrackSort.TITLE); sortMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_added)) }, onClick = { vm.setSort(TrackSort.ADDED); sortMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_duration)) }, onClick = { vm.setSort(TrackSort.DURATION); sortMenu = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.sort_plays)) }, onClick = { vm.setSort(TrackSort.PLAY_COUNT); sortMenu = false })
                    }
                }
                Spacer(Modifier.width(8.dp))
                HeaderIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.filter),
                    onClick = { filterOpen = true },
                    active = bundle.controls.offlineOnly || bundle.controls.sourceId != null,
                )
            },
        )

        if (!bundle.deviceOnline) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 2.dp)) {
                GlassPill(
                    text = stringResource(R.string.home_offline_filter),
                    icon = Icons.Filled.CloudOff,
                    active = true,
                )
            }
        }

        PillTabRow(
            tabs = LibraryTab.entries.map { tabLabel(it) },
            selectedIndex = bundle.controls.tab.ordinal,
            onSelected = { vm.setTab(LibraryTab.entries[it]) },
        )

        Box(Modifier.weight(1f)) {
            when (bundle.controls.tab) {
                LibraryTab.Tracks -> if (trackItems.itemCount == 0) {
                    EmptyState(stringResource(R.string.empty_library), Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(top = 2.dp, bottom = 90.dp),
                        ) {
                            items(count = trackItems.itemCount, key = trackItems.itemKey { it.id }) { index ->
                                val track = trackItems[index]
                                if (track == null) {
                                    TrackRowPlaceholder()
                                } else {
                                    TrackRow(
                                        track,
                                        { vm.play(index) },
                                        { vm.playNext(track) },
                                        { vm.enqueue(track) },
                                        { vm.favorite(track.id) },
                                        { vm.download(track.id) },
                                        onAddToPlaylist = { addTrackId = track.id },
                                        onHide = { vm.hide(track.id) },
                                        playing = track.id == bundle.currentTrackId,
                                    )
                                }
                            }
                        }
                        if (bundle.currentTrackId != null) {
                            LocateButton(
                                onClick = {
                                    vm.locateCurrent { index -> scope.launch { listState.scrollToItem(index) } }
                                },
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                            )
                        }
                    }
                }

                LibraryTab.Albums -> LazyVerticalGrid(
                    GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = albumItems.itemCount, key = albumItems.itemKey { it.id }) { index ->
                        val album = albumItems[index]
                        if (album == null) {
                            Column(Modifier.padding(6.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                        .background(LocalRabBeatPalette.current.fill),
                                )
                            }
                        } else {
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onAlbum(album.id) }
                                    .padding(6.dp),
                            ) {
                                Artwork(
                                    album.artworkUri,
                                    album.title,
                                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
                                    8.dp,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    album.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                                Text(
                                    stringResource(R.string.album_meta, album.artist, album.trackCount),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }

                LibraryTab.Artists -> LazyColumn(state = artistListState, contentPadding = PaddingValues(top = 4.dp, bottom = 30.dp)) {
                    items(count = artistItems.itemCount, key = artistItems.itemKey { it.id }) { index ->
                        val artist = artistItems[index]
                        if (artist == null) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(56.dp).clip(CircleShape)
                                        .background(LocalRabBeatPalette.current.fill),
                                )
                                Spacer(Modifier.width(13.dp))
                                Box(
                                    Modifier.width(140.dp).height(16.dp).clip(RoundedCornerShape(4.dp))
                                        .background(LocalRabBeatPalette.current.fill),
                                )
                            }
                        } else {
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onArtist(artist.id) }
                                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Artwork(artist.artworkUri, artist.name, Modifier.size(56.dp), 28.dp)
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            stringResource(R.string.artist_meta, artist.albumCount, artist.trackCount),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 89.dp)
                                        .height(0.5.dp)
                                        .background(LocalRabBeatPalette.current.separator),
                                )
                            }
                        }
                    }
                }

                LibraryTab.Folders -> FolderList(folders, vm)
            }
        }
    }

    if (filterOpen) {
        AlertDialog(
            onDismissRequest = { filterOpen = false },
            title = { Text(stringResource(R.string.filter)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.filter_offline))
                        Switch(
                            checked = bundle.controls.offlineOnly,
                            onCheckedChange = vm::setOffline,
                            colors = SwitchDefaults.colors(checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759), checkedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedTrackColor = LocalRabBeatPalette.current.fill, uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    }
                    Text(stringResource(R.string.filter_all_sources), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassPill(
                            text = stringResource(R.string.filter_all_sources),
                            active = bundle.controls.sourceId == null,
                            onClick = { vm.setSource(null) },
                        )
                        bundle.sources.forEach { source ->
                            GlassPill(
                                text = source.displayName,
                                active = bundle.controls.sourceId == source.id,
                                onClick = { vm.setSource(source.id) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { filterOpen = false }) { Text(stringResource(R.string.filter_apply)) } },
            dismissButton = {
                TextButton(onClick = {
                    vm.setOffline(false)
                    vm.setSource(null)
                    filterOpen = false
                }) { Text(stringResource(R.string.filter_reset)) }
            },
        )
    }
    addTrackId?.let { trackId -> AddToPlaylistDialog(trackId) { addTrackId = null } }
}

@Composable
private fun FolderList(folders: List<FolderSummary>, vm: LibraryViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var open by remember { mutableStateOf<String?>(null) }
    val current = open
    if (current != null) {
        LaunchedEffect(current) { vm.openFolder(current) }
        val songs = vm.folderTracks.collectAsLazyPagingItems()
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = { open = null },
                )
                Text(
                    current,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (songs.itemCount == 0) {
                EmptyState(stringResource(R.string.empty_folder), Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                    items(count = songs.itemCount, key = songs.itemKey { it.id }) { index ->
                        val track = songs[index]
                        if (track == null) {
                            TrackRowPlaceholder()
                        } else {
                            TrackRow(
                                track,
                                { vm.playInFolder(current, index) },
                                { vm.playNext(track) },
                                { vm.enqueue(track) },
                                { vm.favorite(track.id) },
                                { vm.download(track.id) },
                                onHide = { vm.hide(track.id) },
                            )
                        }
                    }
                }
            }
        }
    } else if (folders.isEmpty()) {
        EmptyState(stringResource(R.string.empty_folder), Modifier.fillMaxSize())
    } else {
        LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 30.dp)) {
            items(folders, key = { it.path }) { group ->
                var folderMenu by remember(group.path) { mutableStateOf(false) }
                var confirmHide by remember(group.path) { mutableStateOf(false) }
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { open = group.path }
                            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = LocalAccentPalette.current.color,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(group.path, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.smart_count, group.trackCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { folderMenu = true }, modifier = Modifier.size(38.dp)) {
                                Icon(
                                    Icons.Filled.MoreHoriz,
                                    contentDescription = stringResource(R.string.cd_more),
                                    tint = LocalRabBeatPalette.current.secondaryLabel,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_hide_folder)) },
                                    onClick = { folderMenu = false; confirmHide = true },
                                )
                            }
                        }
                    }
                    if (confirmHide) {
                        AlertDialog(
                            onDismissRequest = { confirmHide = false },
                            title = { Text(stringResource(R.string.hide_folder_dialog_title)) },
                            text = {
                                Text(
                                    stringResource(
                                        R.string.hide_folder_dialog_body,
                                        group.path,
                                        group.trackCount,
                                    ),
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmHide = false
                                    vm.hideFolder(group.path)
                                    Toast.makeText(
                                        context,
                                        R.string.hidden_toast,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }) { Text(stringResource(R.string.hide_dialog_confirm)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmHide = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            },
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 55.dp)
                            .height(0.5.dp)
                            .background(LocalRabBeatPalette.current.separator),
                    )
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: LibraryTab): String = when (tab) {
    LibraryTab.Tracks -> stringResource(R.string.library_tracks)
    LibraryTab.Albums -> stringResource(R.string.library_albums)
    LibraryTab.Artists -> stringResource(R.string.library_artists)
    LibraryTab.Folders -> stringResource(R.string.library_folders)
}