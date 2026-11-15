package com.rabbeat.app.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.EmptyState
import kotlinx.coroutines.launch

/** Header + playable track list — shared by album/artist/folder/playlist detail. */
@Composable
fun TrackListScreen(
    db: RabBeatDatabase,
    player: PlaybackController,
    title: String,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    inPlaylistId: String? = null,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (tracks.isNotEmpty()) {
                TextButton(onClick = { player.playQueue(tracks.map { it.toDomain(null) }, 0) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" 播放全部")
                }
            }
        }
        if (tracks.isEmpty()) {
            EmptyState(message = "这里还没有歌曲")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                    TrackContextMenu(
                        track = track,
                        db = db,
                        player = player,
                        inPlaylistId = inPlaylistId,
                        onHide = { scope.launch { db.trackDao().setHidden(listOf(track.id), true) } },
                    ) {
                        TrackMenuRow(
                            track = track,
                            playing = track.id == currentTrackId,
                            db = db,
                            onPlay = { player.playQueue(tracks.map { it.toDomain(null) }, i) },
                        )
                    }
                }
            }
        }
    }
}
