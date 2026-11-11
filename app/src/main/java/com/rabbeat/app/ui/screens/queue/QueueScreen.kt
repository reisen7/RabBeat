package com.rabbeat.app.ui.screens.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.repository.PlaylistRepository
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class QueueViewModel @Inject constructor(
    val player: PlayerController,
    private val playlists: PlaylistRepository,
) : ViewModel() {
    val playback = player.state
    fun save(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val ids = player.state.value.queue.map { it.id }
            if (ids.isNotEmpty()) playlists.saveQueue(name, ids)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onBack: () -> Unit, vm: QueueViewModel = hiltViewModel()) {
    val state by vm.playback.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val accent = LocalAccentPalette.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(
                title = stringResource(R.string.queue_title),
                subtitle = stringResource(R.string.smart_count, state.queue.size),
                leading = {
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
                trailing = {
                    GlassPill(
                        text = stringResource(R.string.queue_clear),
                        icon = Icons.Filled.DeleteSweep,
                        onClick = vm.player::clearUpcoming,
                    )
                    Spacer(Modifier.width(4.dp))
                    GlassPill(
                        text = stringResource(R.string.queue_save),
                        icon = Icons.Filled.Save,
                        active = state.queue.isNotEmpty(),
                        onClick = { name = ""; naming = true },
                    )
                },
            )

            if (state.queue.isEmpty()) {
                EmptyState(stringResource(R.string.empty_queue), Modifier.fillMaxSize())
            } else {
                LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
                    itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    vm.player.removeFromQueue(index)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp, vertical = 2.dp)
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                RoundedCornerShape(18.dp),
                                            )
                                            .padding(20.dp),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        Text(
                                            stringResource(R.string.action_remove),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                    }
                                }
                            },
                        ) {
                            QueueItem(
                                index = index,
                                title = track.title,
                                active = state.currentIndex == index,
                                count = state.queue.size,
                                vm = vm,
                                accentColor = accent.color,
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (naming) {
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.queue_save)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.playlist_default)) },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val label = name.ifBlank { "我的队列" }
                    vm.save(label) {
                        naming = false
                        scope.launch { snack.showSnackbar(context.getString(R.string.queue_saved, label)) }
                    }
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun QueueItem(
    index: Int,
    title: String,
    active: Boolean,
    count: Int,
    vm: QueueViewModel,
    accentColor: androidx.compose.ui.graphics.Color,
) {
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }
    var accum by remember(index) { mutableFloatStateOf(0f) }
    val latest = rememberUpdatedState(index)
    val playback = vm.playback.collectAsStateWithLifecycle().value
    val track = playback.queue.getOrNull(index) ?: return
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackRow(
            track = track,
            onClick = { vm.player.playQueue(playback.queue, index) },
            onPlayNext = { vm.player.playNext(track) },
            onEnqueue = {},
            onFavorite = {},
            onDownload = {},
            modifier = Modifier.weight(1f),
            playing = active,
        )
        if (active) {
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .background(accentColor, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    stringResource(R.string.queue_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = stringResource(R.string.cd_drag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .pointerInput(title) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { accum = 0f },
                        onDragCancel = { accum = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accum += amount.y
                            val from = latest.value
                            if (accum > threshold && from < count - 1) {
                                accum = 0f
                                vm.player.moveQueueItem(from, from + 1)
                            } else if (accum < -threshold && from > 0) {
                                accum = 0f
                                vm.player.moveQueueItem(from, from - 1)
                            }
                        },
                    )
                }
                .padding(12.dp),
        )
    }
}
