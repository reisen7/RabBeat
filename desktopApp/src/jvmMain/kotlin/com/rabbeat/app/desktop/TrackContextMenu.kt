package com.rabbeat.app.desktop

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.EmptyState
import kotlinx.coroutines.launch

/**
 * Wraps a composable with the desktop right-click context menu for a track:
 * play next / enqueue / favorite / hide / add to playlist (+ caller extras).
 */
@Composable
fun TrackContextMenu(
    track: TrackEntity,
    db: RabBeatDatabase,
    player: PlaybackController,
    inPlaylistId: String? = null,
    extraMenu: List<ContextMenuItem> = emptyList(),
    onHide: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var pickPlaylist by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val domain = remember(track) { track.toDomain(null) }

    val items = buildList {
        add(ContextMenuItem("下一首播放") { player.playNext(domain) })
        add(ContextMenuItem("添加到队列") { player.enqueue(domain) })
        add(ContextMenuItem(if (track.isFavorite) "取消喜欢" else "喜欢") {
            scope.launch { db.trackDao().toggleFavorite(track.id) }
        })
        if (inPlaylistId != null) {
            add(ContextMenuItem("从歌单移除") {
                scope.launch { db.playlistDao().removeTrack(inPlaylistId, track.id) }
            })
        }
        add(ContextMenuItem("添加到歌单…") { pickPlaylist = true })
        addAll(extraMenu)
        if (onHide != null) add(ContextMenuItem("隐藏") { onHide() })
    }

    ContextMenuArea(items = { items }) { content() }

    if (pickPlaylist) {
        PlaylistPickerDialog(
            db = db,
            onDismiss = { pickPlaylist = false },
            onPick = { playlistId ->
                pickPlaylist = false
                scope.launch { addToPlaylist(db, playlistId, listOf(domain)) }
            },
        )
    }
}

@Composable
private fun PlaylistPickerDialog(
    db: RabBeatDatabase,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            if (playlists.isEmpty()) {
                EmptyState(message = "还没有歌单，先在左侧建一个")
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(playlists, key = { it.id }) { playlist ->
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(playlist.id) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
