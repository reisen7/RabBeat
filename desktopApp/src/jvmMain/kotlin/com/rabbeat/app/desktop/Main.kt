package com.rabbeat.app.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.rabbeat.app.data.local.MIGRATION_1_2
import com.rabbeat.app.data.local.MIGRATION_2_3
import com.rabbeat.app.data.local.MIGRATION_3_4
import com.rabbeat.app.data.local.MIGRATION_4_5
import com.rabbeat.app.data.local.MIGRATION_5_6
import com.rabbeat.app.data.local.MIGRATION_6_7
import com.rabbeat.app.data.local.MIGRATION_7_8
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.MiniPlayerBar
import com.rabbeat.app.ui.theme.RabBeatTheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileSystemView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun openDatabase(): RabBeatDatabase {
    val dir = LibraryScanner.dataDir().apply { mkdirs() }
    return Room.databaseBuilder<RabBeatDatabase>(File(dir, "rabbeat.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
        )
        .fallbackToDestructiveMigration(true)
        .build()
}

private fun pickDirectory(onPicked: (File) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser(FileSystemView.getFileSystemView().homeDirectory).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择音乐文件夹"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            onPicked(chooser.selectedFile)
        }
    }
}

@Composable
private fun App(db: RabBeatDatabase, player: PlaybackController, scope: CoroutineScope) {
    val tracks by db.trackDao().observeAll().collectAsState(initial = emptyList())
    val state by player.state.collectAsState()
    var scanText by remember { mutableStateOf<String?>(null) }
    val scanner = remember { LibraryScanner(db) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("RabBeat", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
            Button(onClick = {
                pickDirectory { dir ->
                    scope.launch {
                        scanText = "扫描中…"
                        val count = scanner.scan(dir) { done, total -> scanText = "扫描中 $done/$total" }
                        scanText = "已导入 $count 首"
                    }
                }
            }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入文件夹")
            }
            Spacer(Modifier.width(12.dp))
            scanText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                TrackRow(
                    track = track,
                    playing = track.id == state.currentTrack?.id,
                    onClick = { player.playQueue(tracks.map { it.toDomain(null) }, i) },
                )
            }
        }

        PlayerBar(state = state, player = player)
        MiniPlayerBar(
            state = state,
            onOpen = {},
            onToggle = { player.toggle() },
            onNext = { player.next() },
            onPrev = { player.previous() },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TrackRow(track: TrackEntity, playing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatMs(track.durationMs), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PlayerBar(state: com.rabbeat.app.playback.PlaybackUiState, player: PlaybackController) {
    Slider(
        value = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
        onValueChange = { fraction ->
            if (state.durationMs > 0) player.seekTo((fraction * state.durationMs).toLong())
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

private fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun main() = application {
    val db = openDatabase()
    val appScope = remember { CoroutineScope(Dispatchers.Main) }
    val player = remember { DesktopPlayerController(db, appScope) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "RabBeat",
        state = rememberWindowState(width = 900.dp, height = 640.dp),
    ) {
        RabBeatTheme {
            Surface(Modifier.fillMaxSize()) {
                App(db, player, appScope)
            }
        }
    }
}
