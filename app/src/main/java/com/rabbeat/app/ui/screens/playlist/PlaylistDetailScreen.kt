package com.rabbeat.app.ui.screens.playlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.domain.repository.PlaylistRepository
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.AccentButton
import com.rabbeat.app.ui.components.AddToPlaylistDialog
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.widget.Toast
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistPage(val playlist: UserPlaylist?, val tracks: List<Track>, val currentTrackId: String?)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val player: PlayerController,
) : ViewModel() {
    fun page(id: String) = combine(playlists.observePlaylists(), playlists.observeTracks(id), player.state) { sheets, tracks, playback ->
        PlaylistPage(sheets.firstOrNull { it.id == id }, tracks, playback.currentTrack?.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistPage(null, emptyList(), null))

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun playNext(track: Track) = player.playNext(track)
    fun enqueue(track: Track) = player.enqueue(track)
    fun remove(playlistId: String, trackId: String) {
        viewModelScope.launch { playlists.removeTrack(playlistId, trackId) }
    }
    fun reorder(playlistId: String, trackIds: List<String>) {
        viewModelScope.launch { playlists.reorderTracks(playlistId, trackIds) }
    }
    fun rename(playlistId: String, name: String) {
        viewModelScope.launch { playlists.rename(playlistId, name) }
    }
    fun delete(playlistId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            playlists.delete(playlistId)
            onDone()
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    vm: PlaylistDetailViewModel = hiltViewModel(),
) {
    val flow = remember(playlistId) { vm.page(playlistId) }
    val page by flow.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(PlaylistSort.Order) }
    var sortMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var name by remember(page.playlist?.name) { mutableStateOf(page.playlist?.name.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addTrackId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var local by remember { mutableStateOf<List<Track>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val canDrag = sort == PlaylistSort.Order && query.isBlank()
    val shown = page.tracks
        .let { tracks ->
            val q = query.trim()
            if (q.isEmpty()) tracks else tracks.filter {
                it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
            }
        }
        .let { tracks ->
            when (sort) {
                PlaylistSort.Order -> tracks
                PlaylistSort.Title -> tracks.sortedBy { it.title.lowercase() }
                PlaylistSort.Artist -> tracks.sortedBy { it.artist.lowercase() }
                PlaylistSort.Album -> tracks.sortedBy { it.album.lowercase() }
            }
        }
    val display = local ?: shown
    LaunchedEffect(page.tracks) { if (!dragging) local = null }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val list = (local ?: shown).toMutableList()
        list.add(to.index.coerceAtMost(list.size), list.removeAt(from.index))
        local = list
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = page.playlist?.name ?: stringResource(R.string.playlist_missing),
            subtitle = stringResource(R.string.smart_count, page.tracks.size),
            leading = {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
            trailing = {
                if (page.playlist?.isSystem != true) {
                    GlassPill(
                        text = stringResource(R.string.playlist_rename),
                        icon = Icons.Filled.Edit,
                        onClick = { renaming = true },
                    )
                    Spacer(Modifier.width(4.dp))
                    GlassPill(
                        text = stringResource(R.string.playlist_delete),
                        icon = Icons.Filled.DeleteOutline,
                        onClick = { confirmDelete = true },
                    )
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AccentButton(
                text = stringResource(R.string.action_play),
                onClick = { vm.play(shown, 0) },
                icon = Icons.Filled.PlayArrow,
                enabled = shown.isNotEmpty(),
                tonal = true,
                modifier = Modifier.weight(1f),
            )
            Box {
                GlassPill(
                    text = stringResource(R.string.sort),
                    icon = Icons.AutoMirrored.Filled.Sort,
                    onClick = { sortMenu = true },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlist_sort_order)) }, onClick = { sort = PlaylistSort.Order; sortMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_title)) }, onClick = { sort = PlaylistSort.Title; sortMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlist_sort_artist)) }, onClick = { sort = PlaylistSort.Artist; sortMenu = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.playlist_sort_album)) }, onClick = { sort = PlaylistSort.Album; sortMenu = false })
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
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
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accent.color),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.playlist_search),
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.secondaryLabel,
                        )
                    }
                    inner()
                },
            )
        }

        if (shown.isEmpty()) {
            EmptyState(stringResource(R.string.playlist_no_tracks), Modifier.weight(1f).fillMaxWidth())
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 90.dp),
                ) {
                    items(display, key = { it.id }) { track ->
                        ReorderableItem(reorderState, key = track.id) { isDragging ->
                            val scale by animateFloatAsState(if (isDragging) 1.02f else 1f)
                            val elevation by animateFloatAsState(if (isDragging) 8f else 0f)
                            TrackRow(
                                track = track,
                                onClick = { vm.play(shown, shown.indexOfFirst { it.id == track.id }) },
                                onPlayNext = { vm.playNext(track) },
                                onEnqueue = { vm.enqueue(track) },
                                onFavorite = {},
                                onDownload = {},
                                onAddToPlaylist = { addTrackId = track.id },
                                onRemove = { vm.remove(playlistId, track.id) },
                                playing = track.id == page.currentTrackId,
                                onLongClick = if (canDrag) {
                                    null
                                } else {
                                    {
                                        Toast.makeText(
                                            context,
                                            R.string.playlist_drag_hint,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .scale(scale)
                                    .shadow(elevation.dp)
                                    .background(if (isDragging) palette.background else Color.Transparent)
                                    .then(
                                        if (canDrag) {
                                            Modifier.longPressDraggableHandle(
                                                onDragStarted = {
                                                    dragging = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDragStopped = {
                                                    dragging = false
                                                    local?.let { vm.reorder(playlistId, it.map(Track::id)) }
                                                },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
                val index = shown.indexOfFirst { it.id == page.currentTrackId }
                if (index >= 0) {
                    FloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(index) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.playlist_locate))
                    }
                }
            }
        }
    }
    if (renaming && page.playlist?.isSystem != true) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.playlist_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(playlistId, name); renaming = false }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete && page.playlist?.isSystem != true) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(stringResource(R.string.playlist_delete_body)) },
            confirmButton = { TextButton(onClick = { vm.delete(playlistId, onBack) }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    addTrackId?.let { trackId -> AddToPlaylistDialog(trackId, onDismiss = { addTrackId = null }) }
}

private enum class PlaylistSort { Order, Title, Artist, Album }
