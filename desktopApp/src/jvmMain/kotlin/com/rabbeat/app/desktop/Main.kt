package com.rabbeat.app.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.rabbeat.app.playback.PlaybackController
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
    var playerOpen by remember { mutableStateOf(false) }
    val scanner = remember { LibraryScanner(db) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
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

        DesktopLibrary(
            db = db,
            player = player,
            tracks = tracks,
            currentTrackId = state.currentTrack?.id,
        )

        MiniPlayerBar(
            state = state,
            onOpen = { playerOpen = true },
            onToggle = { player.toggle() },
            onNext = { player.next() },
            onPrev = { player.previous() },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    AnimatedVisibility(
        visible = playerOpen,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        PlayerOverlay(state = state, player = player, onCollapse = { playerOpen = false })
    }
}

fun formatMs(ms: Long): String {
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
        state = rememberWindowState(width = 960.dp, height = 680.dp),
    ) {
        RabBeatTheme {
            Surface(Modifier.fillMaxSize()) {
                App(db, player, appScope)
            }
        }
    }
}
