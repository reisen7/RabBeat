package com.rabbeat.app.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.desktop.library.DesktopLibrary
import com.rabbeat.app.desktop.library.LibraryScanner
import com.rabbeat.app.desktop.library.LocalLikedPlaylist
import com.rabbeat.app.desktop.library.TrackListScreen
import com.rabbeat.app.desktop.player.DesktopPlayerController
import com.rabbeat.app.desktop.playlist.PlaylistDetailView
import com.rabbeat.app.desktop.playlist.PlaylistListView
import com.rabbeat.app.desktop.sys.DesktopRoute
import com.rabbeat.app.desktop.sys.DesktopSettings
import com.rabbeat.app.desktop.sys.DesktopUpdate
import com.rabbeat.app.desktop.sys.HiddenSongsView
import com.rabbeat.app.desktop.sys.SettingsView
import com.rabbeat.app.desktop.sys.pickDirectory
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.ui.components.LocalCardOpacity
import com.rabbeat.app.ui.components.LocalHasBackgroundImage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun debugStartProp(): String = System.getProperty("rabbeat.start").orEmpty()

private fun debugStartRoute(): DesktopRoute = when (debugStartProp()) {
    "library" -> DesktopRoute.Library
    "playlists" -> DesktopRoute.Playlists
    "recent" -> DesktopRoute.RecentAdded
    "settings" -> DesktopRoute.Settings
    "search" -> DesktopRoute.Search
    else -> DesktopRoute.Home
}

private fun debugAutoplay(): Boolean = System.getProperty("rabbeat.autoplay") == "true"


private fun TrackEntity.folderPathOf(): String = folder?.trim()?.takeIf { it.isNotEmpty() } ?: "本地"

@Composable
fun AppShell(
    db: RabBeatDatabase,
    player: DesktopPlayerController,
    scope: CoroutineScope,
    settings: DesktopSettings,
    activeThemeMode: String = settings.themeMode,
    playerOpen: MutableState<Boolean>,
    queueOpen: MutableState<Boolean>,
    keys: ShellKeys,
    onThemeChanged: () -> Unit,
    onAppearanceChanged: () -> Unit,
) {
    val tracks by db.trackDao().observeAll().collectAsState(initial = emptyList())
    val albums by db.albumDao().observeAll().collectAsState(initial = emptyList())
    val state by player.state.collectAsState()
    var scanText by remember { mutableStateOf<String?>(null) }
    var globalQuery by remember {
        mutableStateOf(
            if (debugStartProp() == "search") System.getProperty("rabbeat.searchQuery") ?: "夜" else "",
        )
    }
    var isPlayerOpen by playerOpen
    var isQueueOpen by queueOpen
    val history = remember { NavHistory(debugStartRoute()) }
    val scanner = remember { LibraryScanner(db) }
    val liked = remember { LikedPlaylist(db.playlistDao(), db.trackDao()) }

    val triggerImport: () -> Unit = {
        scope.launch {
            pickDirectory("选择音乐文件夹")?.let { dir ->
                scanText = "扫描中…"
                val count = scanner.scan(dir) { done, total -> scanText = "扫描中 $done/$total" }
                scanText = "已导入 $count 首"
            }
        }
    }

    LaunchedEffect(Unit) {
        scanner.prune()
        liked.ensure()
    }

    var albumStartDone by remember { mutableStateOf(false) }
    LaunchedEffect(albums) {
        if (!albumStartDone && debugStartProp() == "album" && albums.isNotEmpty()) {
            albumStartDone = true
            history.push(DesktopRoute.AlbumDetail(albums.first().id))
        }
    }

    var autoplayDone by remember { mutableStateOf(false) }
    LaunchedEffect(tracks.isNotEmpty()) {
        if (!autoplayDone && debugAutoplay() && tracks.isNotEmpty()) {
            autoplayDone = true
            player.playQueue(tracks.map { it.toDomain(null) }, 0)
        }
    }

    LaunchedEffect(globalQuery) {
        if (globalQuery.isNotBlank() && history.current !is DesktopRoute.Search) {
            history.push(DesktopRoute.Search)
        } else if (globalQuery.isBlank() && history.current is DesktopRoute.Search) {
            if (history.canBack) history.back() else history.topLevel(DesktopRoute.Library)
        }
    }

    val bgPath = settings.bgImage
    val bgBitmap = remember(bgPath) {
        bgPath.takeIf { it.isNotBlank() }
            ?.let { runCatching { androidx.compose.ui.res.loadImageBitmap(File(it).inputStream()) }.getOrNull() }
    }

    CompositionLocalProvider(
        LocalLikedPlaylist provides liked,
        LocalHasBackgroundImage provides (bgBitmap != null),
        LocalCardOpacity provides settings.cardOpacity,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        MaterialTheme.colorScheme.background.copy(alpha = settings.dimLevel.coerceIn(0f, 0.9f)),
                    ),
                )
            }

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Sidebar(
                        db = db,
                        route = history.current,
                        onTopLevel = { history.topLevel(it) },
                        onOpenLiked = {
                            scope.launch { liked.ensure() }
                            history.push(DesktopRoute.PlaylistDetail(LikedPlaylist.ID))
                        },
                        onOpenPlaylist = { history.push(DesktopRoute.PlaylistDetail(it)) },
                    )
                    Column(Modifier.weight(1f).fillMaxSize()) {
                        TopBar(
                            history = history,
                            query = globalQuery,
                            onQueryChange = { globalQuery = it },
                            onSearchSubmit = {
                                if (globalQuery.isNotBlank() && history.current !is DesktopRoute.Search) {
                                    history.push(DesktopRoute.Search)
                                }
                            },
                            scanText = scanText,
                            onImport = triggerImport,
                            keys = keys,
                        )
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (val r = history.current) {
                                is DesktopRoute.Home -> HomePage(
                                    db = db,
                                    player = player,
                                    tracks = tracks,
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.isPlaying,
                                    onImport = triggerImport,
                                    onOpenPlaylists = { history.push(DesktopRoute.Playlists) },
                                    onOpenPlaylist = { history.push(DesktopRoute.PlaylistDetail(it)) },
                                    onOpenRecent = { history.push(DesktopRoute.RecentAdded) },
                                    onOpenMostPlayed = { history.push(DesktopRoute.MostPlayed) },
                                )
                                is DesktopRoute.Library -> DesktopLibrary(
                                    db = db,
                                    player = player,
                                    tracks = tracks,
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.isPlaying,
                                    query = globalQuery,
                                    onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                    onOpenArtist = { history.push(DesktopRoute.ArtistDetail(it)) },
                                    onOpenFolder = { history.push(DesktopRoute.FolderDetail(it)) },
                                )
                                is DesktopRoute.Search -> SearchPage(
                                    db = db,
                                    player = player,
                                    tracks = tracks,
                                    query = globalQuery,
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.isPlaying,
                                    onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                    onOpenArtist = { history.push(DesktopRoute.ArtistDetail(it)) },
                                    onOpenPlaylist = { history.push(DesktopRoute.PlaylistDetail(it)) },
                                )
                                is DesktopRoute.Playlists -> PlaylistListView(
                                    db = db,
                                    player = player,
                                    onOpen = { history.push(DesktopRoute.PlaylistDetail(it)) },
                                )
                                is DesktopRoute.RecentAdded -> {
                                    val recent by db.trackDao().observeRecentlyAdded(500).collectAsState(initial = emptyList())
                                    TrackListScreen(
                                        db = db,
                                        player = player,
                                        title = "最近添加",
                                        badge = "精选",
                                        subtitle = "按导入时间倒序",
                                        fallbackIcon = Icons.Default.History,
                                        tracks = recent,
                                        currentTrackId = state.currentTrack?.id,
                                        isPlaying = state.isPlaying,
                                        onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                        onBack = { history.back() },
                                    )
                                }
                                is DesktopRoute.MostPlayed -> {
                                    val top by db.trackDao()
                                        .observeQuery(TrackQuerySql.select(TrackQuery(sort = TrackSort.PLAY_COUNT)))
                                        .collectAsState(initial = emptyList())
                                    TrackListScreen(
                                        db = db,
                                        player = player,
                                        title = "最常播放",
                                        badge = "排行",
                                        subtitle = "按累计播放次数排序",
                                        fallbackIcon = Icons.AutoMirrored.Filled.TrendingUp,
                                        tracks = top,
                                        currentTrackId = state.currentTrack?.id,
                                        isPlaying = state.isPlaying,
                                        onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                        onBack = { history.back() },
                                    )
                                }
                                is DesktopRoute.AlbumDetail -> {
                                    val album by db.albumDao().observeById(r.albumId).collectAsState(initial = null)
                                    val albumTracks by db.trackDao().observeByAlbum(r.albumId)
                                        .collectAsState(initial = emptyList())
                                    TrackListScreen(
                                        db = db,
                                        player = player,
                                        title = album?.title ?: albumTracks.firstOrNull()?.album ?: "专辑",
                                        badge = "专辑",
                                        subtitle = listOfNotNull(
                                            album?.artist ?: albumTracks.firstOrNull()?.artist,
                                            album?.year?.toString(),
                                        ).joinToString(" · "),
                                        coverUri = album?.artworkUri ?: albumTracks.firstOrNull()?.artworkUri,
                                        tracks = albumTracks,
                                        currentTrackId = state.currentTrack?.id,
                                        isPlaying = state.isPlaying,
                                        showAlbum = false,
                                        onBack = { history.back() },
                                    )
                                }
                                is DesktopRoute.ArtistDetail -> {
                                    val artist by db.artistDao().observeById(r.artistId).collectAsState(initial = null)
                                    val artistTracks by db.trackDao().observeByArtist(r.artistId)
                                        .collectAsState(initial = emptyList())
                                    val albumCount = artistTracks.map { it.album }.distinct().size
                                    TrackListScreen(
                                        db = db,
                                        player = player,
                                        title = artist?.name ?: artistTracks.firstOrNull()?.artist ?: "艺人",
                                        badge = "艺人",
                                        subtitle = "$albumCount 张专辑",
                                        coverUri = artist?.artworkUri
                                            ?: artistTracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                                        isCircleCover = true,
                                        tracks = artistTracks,
                                        currentTrackId = state.currentTrack?.id,
                                        isPlaying = state.isPlaying,
                                        onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                        onBack = { history.back() },
                                    )
                                }
                                is DesktopRoute.FolderDetail -> TrackListScreen(
                                    db = db,
                                    player = player,
                                    title = r.path.ifBlank { "本地" },
                                    badge = "文件夹",
                                    subtitle = "本地目录",
                                    fallbackIcon = Icons.Default.Folder,
                                    tracks = tracks.filter { it.folderPathOf() == r.path.ifBlank { "本地" } },
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.isPlaying,
                                    onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                    onBack = { history.back() },
                                )
                                is DesktopRoute.PlaylistDetail -> PlaylistDetailView(
                                    db = db,
                                    player = player,
                                    playlistId = r.playlistId,
                                    currentTrackId = state.currentTrack?.id,
                                    isPlaying = state.isPlaying,
                                    onOpenAlbum = { history.push(DesktopRoute.AlbumDetail(it)) },
                                    onBack = { history.back() },
                                )
                                is DesktopRoute.HiddenSongs -> HiddenSongsView(db = db, onBack = { history.back() })
                                is DesktopRoute.Settings -> SettingsView(
                                    settings = settings,
                                    activeThemeMode = activeThemeMode,
                                    scanner = scanner,
                                    db = db,
                                    onThemeChange = onThemeChanged,
                                    onAppearanceChange = onAppearanceChanged,
                                    onOpenHidden = { history.push(DesktopRoute.HiddenSongs) },
                                    onBack = { history.back() },
                                )
                            }
                        }
                    }
                }
                PlayerBar(
                    db = db,
                    player = player,
                    state = state,
                    queueOpen = isQueueOpen,
                    nowPlayingOpen = isPlayerOpen,
                    onToggleNowPlaying = { isPlayerOpen = !isPlayerOpen },
                    onToggleQueue = { isQueueOpen = !isQueueOpen },
                )
            }

            Box(Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                AnimatedVisibility(
                    visible = isPlayerOpen,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    NowPlayingPage(
                        db = db,
                        player = player,
                        state = state,
                        onCollapse = { isPlayerOpen = false },
                    )
                }
                QueueDrawer(
                    state = state,
                    player = player,
                    open = isQueueOpen,
                    onClose = { isQueueOpen = false },
                )
            }
        }
    }
}
