package com.rabbeat.app.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.domain.repository.PlaylistRepository
import com.rabbeat.app.ui.components.CreatePlaylistDialog
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.SelectionCheck
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
) : ViewModel() {
    val sheets = playlists.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) {
        viewModelScope.launch { playlists.create(name) }
    }

    fun deleteMany(ids: List<String>) {
        viewModelScope.launch { playlists.deleteMany(ids) }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch { playlists.reorder(ids) }
    }
}

@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onPlaylist: (String) -> Unit,
    vm: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists by vm.sheets.collectAsStateWithLifecycle()
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val system = playlists.filter { it.isSystem }
    val normal = playlists.filter { !it.isSystem }
    var ordered by remember { mutableStateOf<List<UserPlaylist>?>(null) }
    val shown = ordered ?: normal

    fun exitEdit() {
        editing = false
        ordered = null
        selected = emptySet()
    }
    BackHandler(enabled = editing) { exitEdit() }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val pinned = system.size
        val source = from.index - pinned
        val target = (to.index - pinned).coerceAtLeast(0)
        if (source >= 0 && source != target) {
            val list = shown.toMutableList()
            list.add(target.coerceAtMost(list.size), list.removeAt(source))
            ordered = list
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.profile_section_playlists),
            subtitle = stringResource(R.string.playlists_count, playlists.size),
            leading = {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = { if (editing) exitEdit() else onBack() },
                )
            },
            trailing = {
                if (editing) {
                    TextButton(onClick = { exitEdit() }) {
                        Text(
                            stringResource(R.string.action_done),
                            color = accent.color,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    HeaderIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.playlist_new),
                        onClick = { creating = true },
                    )
                    if (normal.isNotEmpty()) {
                        TextButton(onClick = { editing = true }) {
                            Text(stringResource(R.string.action_edit), color = accent.color)
                        }
                    }
                }
            },
        )
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(system, key = { it.id }) { playlist ->
                PlaylistManageRow(
                    playlist = playlist,
                    editing = editing,
                    selected = false,
                    onClick = { onPlaylist(playlist.id) },
                    chevron = !editing,
                )
            }
            items(shown, key = { it.id }) { playlist ->
                ReorderableItem(reorderState, key = playlist.id) { _ ->
                    PlaylistManageRow(
                        playlist = playlist,
                        editing = editing,
                        selected = playlist.id in selected,
                        onClick = {
                            if (editing) {
                                selected = if (playlist.id in selected) {
                                    selected - playlist.id
                                } else {
                                    selected + playlist.id
                                }
                            } else {
                                onPlaylist(playlist.id)
                            }
                        },
                        chevron = !editing,
                        dragHandle = if (editing) {
                            {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.cd_reorder),
                                    tint = palette.secondaryLabel,
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            vm.reorder(shown.map { it.id })
                                        },
                                    ),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
            if (normal.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.profile_playlist_hint_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.secondaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        if (editing) {
            Column(Modifier.fillMaxWidth().background(palette.bar)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(palette.separator),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val allSelected = selected.size == normal.size && normal.isNotEmpty()
                    TextButton(onClick = {
                        selected = if (allSelected) emptySet() else normal.map { it.id }.toSet()
                    }) {
                        Text(
                            stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all),
                            color = accent.color,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(
                            stringResource(R.string.playlists_delete_count, selected.size),
                            color = if (selected.isEmpty()) palette.tertiaryLabel else Color(0xFFFF3B30),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        CreatePlaylistDialog(
            onDismiss = { creating = false },
            onConfirm = { vm.create(it) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.playlists_delete_title, selected.size)) },
            text = { Text(stringResource(R.string.playlist_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    val remaining = normal.count { it.id !in selected }
                    vm.deleteMany(selected.toList())
                    ordered = ordered?.filter { it.id !in selected }
                    selected = emptySet()
                    confirmDelete = false
                    if (remaining == 0) exitEdit()
                }) { Text(stringResource(R.string.action_delete), color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistManageRow(
    playlist: UserPlaylist,
    editing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    chevron: Boolean,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(enabled = !editing || !playlist.isSystem, onClick = onClick)
                .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = editing) {
                if (playlist.isSystem) {
                    Spacer(Modifier.size(24.dp))
                } else {
                    SelectionCheck(checked = selected)
                }
            }
            if (editing) Spacer(Modifier.width(14.dp))
            PlaylistCover(playlist, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.playlist_count, playlist.trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                )
            }
            if (chevron) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.tertiaryLabel,
                    modifier = Modifier.size(20.dp),
                )
            }
            dragHandle?.invoke()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 88.dp)
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}
