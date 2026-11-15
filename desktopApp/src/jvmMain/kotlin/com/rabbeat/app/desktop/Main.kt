package com.rabbeat.app.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
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
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.sharedui.generated.resources.Res
import com.rabbeat.app.sharedui.generated.resources.ic_launcher_foreground
import com.rabbeat.app.ui.components.MiniPlayerBar
import com.rabbeat.app.ui.theme.LocalAccentPalette
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
private fun App(
    db: RabBeatDatabase,
    player: DesktopPlayerController,
    scope: CoroutineScope,
    settings: DesktopSettings,
    onThemeChanged: () -> Unit,
) {
    val tracks by db.trackDao().observeAll().collectAsState(initial = emptyList())
    val state by player.state.collectAsState()
    var scanText by remember { mutableStateOf<String?>(null) }
    var playerOpen by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<DesktopRoute>(DesktopRoute.Library) }
    val backStack = remember { mutableStateListOf<DesktopRoute>() }
    val scanner = remember { LibraryScanner(db) }

    fun push(r: DesktopRoute) { backStack.add(route); route = r }
    fun pop() { route = backStack.removeLastOrNull() ?: DesktopRoute.Library }
    fun topLevel(r: DesktopRoute) { backStack.clear(); route = r }

    Row(Modifier.fillMaxSize()) {
        // Left nav rail — RabBeat side navigation
        Column(
            Modifier.width(172.dp).fillMaxSize().padding(vertical = 12.dp),
        ) {
            Text(
                "RabBeat",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            NavRailItem("曲库", route is DesktopRoute.Library) { topLevel(DesktopRoute.Library) }
            NavRailItem("最近添加", route is DesktopRoute.RecentAdded) { topLevel(DesktopRoute.RecentAdded) }
            NavRailItem("最常播放", route is DesktopRoute.MostPlayed) { topLevel(DesktopRoute.MostPlayed) }
            NavRailItem("歌单", route is DesktopRoute.Playlists || route is DesktopRoute.PlaylistDetail) { topLevel(DesktopRoute.Playlists) }
            NavRailItem("已隐藏", route is DesktopRoute.HiddenSongs) { topLevel(DesktopRoute.HiddenSongs) }
            Spacer(Modifier.weight(1f))
            NavRailItem("设置", route is DesktopRoute.Settings) { topLevel(DesktopRoute.Settings) }
        }

        Column(Modifier.weight(1f).fillMaxSize()) {
            if (route is DesktopRoute.Library) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
            }

            Box(Modifier.weight(1f)) {
                when (val r = route) {
                    is DesktopRoute.Library -> DesktopLibrary(
                        db = db,
                        player = player,
                        tracks = tracks,
                        currentTrackId = state.currentTrack?.id,
                        onOpenAlbum = { push(DesktopRoute.AlbumDetail(it)) },
                        onOpenArtist = { push(DesktopRoute.ArtistDetail(it)) },
                        onOpenFolder = { push(DesktopRoute.FolderDetail(it)) },
                    )
                    is DesktopRoute.Playlists -> PlaylistListView(db = db, onOpen = { push(DesktopRoute.PlaylistDetail(it)) })
                    is DesktopRoute.RecentAdded -> {
                        val recent by db.trackDao().observeRecentlyAdded(500).collectAsState(initial = emptyList())
                        TrackListScreen(
                            db = db, player = player, title = "最近添加",
                            tracks = recent, currentTrackId = state.currentTrack?.id,
                            onBack = ::pop,
                        )
                    }
                    is DesktopRoute.MostPlayed -> {
                        val top by db.trackDao()
                            .observeQuery(TrackQuerySql.select(TrackQuery(sort = TrackSort.PLAY_COUNT)))
                            .collectAsState(initial = emptyList())
                        TrackListScreen(
                            db = db, player = player, title = "最常播放",
                            tracks = top, currentTrackId = state.currentTrack?.id,
                            onBack = ::pop,
                        )
                    }
                    is DesktopRoute.AlbumDetail -> TrackListScreen(
                        db = db, player = player,
                        title = tracks.firstOrNull { it.albumId == r.albumId }?.album ?: "专辑",
                        tracks = tracks.filter { it.albumId == r.albumId },
                        currentTrackId = state.currentTrack?.id,
                        onBack = ::pop,
                    )
                    is DesktopRoute.ArtistDetail -> TrackListScreen(
                        db = db, player = player,
                        title = tracks.firstOrNull { it.artistId == r.artistId }?.artist ?: "艺人",
                        tracks = tracks.filter { it.artistId == r.artistId },
                        currentTrackId = state.currentTrack?.id,
                        onBack = ::pop,
                    )
                    is DesktopRoute.FolderDetail -> TrackListScreen(
                        db = db, player = player,
                        title = r.path.ifBlank { "本地" },
                        tracks = tracks.filter { it.folderPathOf() == r.path.ifBlank { "本地" } },
                        currentTrackId = state.currentTrack?.id,
                        onBack = ::pop,
                    )
                    is DesktopRoute.PlaylistDetail -> PlaylistDetailView(
                        db = db, player = player, playlistId = r.playlistId,
                        currentTrackId = state.currentTrack?.id, onBack = ::pop,
                    )
                    is DesktopRoute.HiddenSongs -> HiddenSongsView(db = db, onBack = ::pop)
                    is DesktopRoute.Settings -> SettingsView(
                        settings = settings,
                        onThemeChange = onThemeChanged,
                        onOpenHidden = { push(DesktopRoute.HiddenSongs) },
                        onBack = ::pop,
                    )
                }
            }

            MiniPlayerBar(
                state = state,
                onOpen = { playerOpen = true },
                onToggle = { player.toggle() },
                onNext = { player.next() },
                onPrev = { player.previous() },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }

    AnimatedVisibility(
        visible = playerOpen,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        PlayerOverlay(
            state = state,
            player = player,
            onCollapse = { playerOpen = false },
            onVolume = { player.setVolume(it) },
        )
    }
}

@Composable
private fun NavRailItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalAccentPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.color.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) accent.color else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun TrackEntity.folderPathOf(): String = folder?.trim()?.takeIf { it.isNotEmpty() } ?: "本地"

fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

fun main() = application {
    val db = openDatabase()
    val appScope = remember { CoroutineScope(Dispatchers.Main) }
    val player = remember { DesktopPlayerController(db, appScope) }
    val settings = remember { DesktopSettings() }
    var themeTick by remember { mutableStateOf(0) }
    var windowVisible by remember { mutableStateOf(true) }
    val playState by player.state.collectAsState()

    Tray(
        icon = painterResource(Res.drawable.ic_launcher_foreground),
        tooltip = "RabBeat",
        onAction = { windowVisible = true },
        menu = {
            Item("显示 RabBeat", onClick = { windowVisible = true })
            Item(if (playState.isPlaying) "暂停" else "播放", onClick = { player.toggle() })
            Item("下一首", onClick = { player.next() })
            Item("上一首", onClick = { player.previous() })
            Separator()
            Item("退出", onClick = { player.shutdown(); exitApplication() })
        },
    )

    if (windowVisible) Window(
        onCloseRequest = { windowVisible = false },  // close → minimize to tray
        title = "RabBeat",
        state = rememberWindowState(width = 1080.dp, height = 720.dp),
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyUp) return@Window false
            when {
                e.isCtrlPressed && e.key == Key.DirectionRight -> { player.next(); true }
                e.isCtrlPressed && e.key == Key.DirectionLeft -> { player.previous(); true }
                e.isCtrlPressed && e.key == Key.Spacebar -> { player.toggle(); true }
                else -> false
            }
        },
    ) {
        val themeMode = remember(themeTick) { ThemeMode.valueOf(settings.themeMode) }
        val accent = remember(themeTick) { AccentTheme.valueOf(settings.accent) }
        RabBeatTheme(themeMode = themeMode, accentTheme = accent) {
            Surface(Modifier.fillMaxSize()) {
                App(db, player, appScope, settings, onThemeChanged = { themeTick++ })
            }
        }
    }
}
