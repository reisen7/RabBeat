package com.rabbeat.app.desktop.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.desktop.ui.TrackTable
import com.rabbeat.app.desktop.ui.hand
import com.rabbeat.app.domain.model.FolderSummary
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TABS = listOf("歌曲", "专辑", "艺人", "文件夹")

@Composable
fun DesktopLibrary(
    db: RabBeatDatabase,
    player: PlaybackController,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    isPlaying: Boolean,
    query: String,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val albums by db.albumDao().observeAll().collectAsState(initial = emptyList())
    val artists by db.artistDao().observeAll().collectAsState(initial = emptyList())
    val folders by db.trackDao().observeFolders().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TrackSort.TITLE) }
    var ascending by remember { mutableStateOf(true) }

    val filtered by produceState(initialValue = emptyList<TrackEntity>(), tracks, query, sort, ascending) {
        value = withContext(Dispatchers.Default) {
            val base = if (query.isBlank()) tracks
            else tracks.filter {
                it.title.contains(query, true) || it.artist.contains(query, true) ||
                    it.album.contains(query, true)
            }
            val cmp: Comparator<TrackEntity> = when (sort) {
                TrackSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                TrackSort.ADDED -> compareBy { it.addedAt }
                TrackSort.DURATION -> compareBy { it.durationMs }
                TrackSort.PLAY_COUNT -> compareBy { it.playCount }
            }
            base.sortedWith(if (ascending) cmp else cmp.reversed())
        }
    }

    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val sortLabels = mapOf(
        TrackSort.TITLE to "标题",
        TrackSort.ADDED to "添加时间",
        TrackSort.DURATION to "时长",
        TrackSort.PLAY_COUNT to "播放次数",
    )

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "本地音乐",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "共 ${tracks.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                )
                Spacer(Modifier.weight(1f))
                if (tab == 0) {
                    Box {
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(palette.fill)
                                .hand()
                                .clickable { sortMenu = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "排序",
                                tint = palette.secondaryLabel,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${sortLabels[sort]} ${if (ascending) "↑" else "↓"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = palette.secondaryLabel,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                TrackSort.TITLE to "标题",
                                TrackSort.ADDED to "添加时间",
                                TrackSort.DURATION to "时长",
                                TrackSort.PLAY_COUNT to "播放次数",
                            ).forEach { (s, label) ->
                                DropdownMenuItem(
                                    text = { Text(label + if (sort == s) (if (ascending) " ↑" else " ↓") else "") },
                                    onClick = {
                                        if (sort == s) ascending = !ascending else {
                                            sort = s
                                            ascending = s == TrackSort.TITLE
                                        }
                                        sortMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                TABS.forEachIndexed { idx, label ->
                    val selected = tab == idx
                    Column(
                        Modifier
                            .width(88.dp)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .hand()
                            .clickable { tab = idx },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else palette.secondaryLabel,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        Box(
                            Modifier
                                .width(28.dp)
                                .height(2.5.dp)
                                .clip(CircleShape)
                                .background(if (selected) accent.color else Color.Transparent),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> TrackTable(
                    db = db,
                    player = player,
                    tracks = filtered,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    showAlbum = true,
                    sort = sort,
                    ascending = ascending,
                    onSortChange = { s ->
                        if (sort == s) ascending = !ascending else {
                            sort = s
                            ascending = s == TrackSort.TITLE
                        }
                    },
                    onOpenAlbum = onOpenAlbum,
                    emptyMessage = if (query.isBlank()) "没有歌曲，点上方「导入文件夹」扫描本地音乐" else "没有匹配的歌曲",
                )
                1 -> AlbumGrid(
                    albums = albums,
                    tracks = tracks,
                    player = player,
                    onOpen = onOpenAlbum,
                )
                2 -> ArtistGrid(
                    artists = artists,
                    albums = albums,
                    onOpen = onOpenArtist,
                )
                else -> FolderList(
                    db = db,
                    folders = folders,
                    tracks = tracks,
                    onOpen = { folder -> onOpenFolder(folder.path) },
                )
            }
        }
    }
}

private val TrackEntity.folderPath: String
    get() = folder?.trim()?.takeIf { it.isNotEmpty() } ?: "本地"

@Composable
private fun AlbumGrid(
    albums: List<AlbumEntity>,
    tracks: List<TrackEntity>,
    player: PlaybackController,
    onOpen: (String) -> Unit,
) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(message = "没有专辑，导入音乐后自动按标签分组")
        }
        return
    }
    val gridState = rememberLazyGridState()
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(172.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(albums, key = { it.id }) { album ->
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .hoverable(interactionSource)
                        .hand()
                        .clickable { onOpen(album.id) },
                ) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                        Artwork(
                            artworkUri = album.artworkUri,
                            title = album.title,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 12.dp,
                        )
                        if (hovered) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
                        }
                        Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = hovered,
                                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.8f),
                                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.8f),
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .shadow(8.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(accent.color)
                                        .hand()
                                        .clickable {
                                            val albumTracks = tracks
                                                .filter { it.albumId == album.id }
                                                .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
                                            if (albumTracks.isNotEmpty()) {
                                                player.playQueue(albumTracks.map { it.toDomain(null) }, 0)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "播放专辑",
                                        tint = accent.onColor,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        album.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${album.artist} · ${album.trackCount} 首",
                        fontSize = 12.sp,
                        color = palette.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(gridState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun ArtistGrid(
    artists: List<ArtistEntity>,
    albums: List<AlbumEntity>,
    onOpen: (String) -> Unit,
) {
    if (artists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(message = "没有艺人，导入音乐后自动按标签分组")
        }
        return
    }
    val gridState = rememberLazyGridState()
    val palette = LocalRabBeatPalette.current
    val fallbackArt = remember(albums) {
        albums.filter { !it.artworkUri.isNullOrBlank() }
            .associate { it.artist to it.artworkUri }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists, key = { it.id }) { artist ->
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val avatarUri = artist.artworkUri ?: fallbackArt[artist.name]
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                            else Color.Transparent,
                        )
                        .hoverable(interactionSource)
                        .hand()
                        .clickable { onOpen(artist.id) }
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Artwork(
                        artworkUri = avatarUri,
                        title = artist.name,
                        modifier = Modifier.size(120.dp).clip(CircleShape),
                        cornerRadius = 60.dp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${artist.albumCount} 张专辑 · ${artist.trackCount} 首",
                        fontSize = 12.sp,
                        color = palette.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(gridState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun FolderList(
    db: RabBeatDatabase,
    folders: List<FolderSummary>,
    tracks: List<TrackEntity>,
    onOpen: (FolderSummary) -> Unit,
) {
    if (folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(message = "没有文件夹，导入音乐后按目录分组")
        }
        return
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(folders, key = { it.path }) { folder ->
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem("隐藏此文件夹") {
                                scope.launch {
                                    val ids = tracks
                                        .filter { it.folderPath == folder.path.ifBlank { "本地" } }
                                        .map { it.id }
                                    if (ids.isNotEmpty()) db.trackDao().setHidden(ids, true)
                                }
                            },
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                else palette.card.copy(alpha = 0.55f),
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                RoundedCornerShape(12.dp),
                            )
                            .hoverable(interactionSource)
                            .hand()
                            .clickable { onOpen(folder) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.soft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = accent.color,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                folder.path.ifBlank { "本地" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${folder.trackCount} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = palette.secondaryLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}
