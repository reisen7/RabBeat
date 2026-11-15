package com.rabbeat.app.desktop.library
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.domain.model.FolderSummary
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.PillTabRow
import kotlinx.coroutines.launch

private val TABS = listOf("歌曲", "专辑", "艺人", "文件夹")

@Composable
fun DesktopLibrary(
    db: RabBeatDatabase,
    player: PlaybackController,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val albums by db.albumDao().observeAll().collectAsState(initial = emptyList())
    val artists by db.artistDao().observeAll().collectAsState(initial = emptyList())
    val folders by db.trackDao().observeFolders().collectAsState(initial = emptyList())
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(TrackSort.TITLE) }
    var ascending by remember { mutableStateOf(true) }
    val pager = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    // Filter+sort off the UI thread — track churn (e.g. playCount updates on every
    // play) otherwise re-sorts the whole library on the composition thread.
    val filtered by androidx.compose.runtime.produceState(initialValue = emptyList<TrackEntity>(), tracks, query, sort, ascending) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
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

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillTabRow(
                tabs = TABS,
                selectedIndex = tab,
                onSelected = {
                    tab = it
                    scope.launch { pager.animateScrollToPage(it) }
                },
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "排序")
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf(
                        TrackSort.TITLE to "标题", TrackSort.ADDED to "添加时间",
                        TrackSort.DURATION to "时长", TrackSort.PLAY_COUNT to "播放次数",
                    ).forEach { (s, label) ->
                        DropdownMenuItem(
                            text = { Text(label + if (sort == s) (if (ascending) " ↑" else " ↓") else "") },
                            onClick = {
                                if (sort == s) ascending = !ascending else { sort = s; ascending = s == TrackSort.TITLE }
                                sortMenu = false
                            },
                        )
                    }
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text("搜索歌曲、艺人、专辑") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        androidx.compose.runtime.LaunchedEffect(pager.currentPage) { tab = pager.currentPage }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = query.isBlank(),
        ) { page ->
            when (page) {
                0 -> TrackList(db, player, filtered, currentTrackId) { i ->
                    player.playQueue(filtered.map { it.toDomain(null) }, i)
                }
                1 -> AlbumList(albums) { album -> album.id?.let(onOpenAlbum) }
                2 -> ArtistList(artists) { artist -> artist.id?.let(onOpenArtist) }
                else -> FolderList(db, folders, tracks) { folder -> onOpenFolder(folder.path) }
            }
        }
    }
}

private val TrackEntity.folderPath: String
    get() = folder?.trim()?.takeIf { it.isNotEmpty() } ?: "本地"

@Composable
private fun TrackList(
    db: RabBeatDatabase,
    player: PlaybackController,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    onPlay: (Int) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyState(message = "没有歌曲，点上方「导入文件夹」扫描本地音乐")
        return
    }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
            TrackContextMenu(
                track = track,
                db = db,
                player = player,
                onHide = { scope.launch { db.trackDao().setHidden(listOf(track.id), true) } },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(i) }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        artworkUri = track.artworkUri,
                        title = track.title,
                        modifier = Modifier.size(46.dp),
                        cornerRadius = 10.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (track.id == currentTrackId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
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
        }
    }
}

@Composable
private fun AlbumList(albums: List<AlbumEntity>, onOpen: (AlbumEntity) -> Unit) {
    if (albums.isEmpty()) {
        EmptyState(message = "没有专辑，导入音乐后自动按标签分组")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.id }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(album) }
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    artworkUri = album.artworkUri,
                    title = album.title,
                    modifier = Modifier.size(52.dp),
                    cornerRadius = 10.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${album.artist} · ${album.trackCount} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<ArtistEntity>, onOpen: (ArtistEntity) -> Unit) {
    if (artists.isEmpty()) {
        EmptyState(message = "没有艺人，导入音乐后自动按标签分组")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(artist) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${artist.albumCount} 张专辑 · ${artist.trackCount} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
        EmptyState(message = "没有文件夹，导入音乐后按目录分组")
        return
    }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
            androidx.compose.foundation.ContextMenuArea(
                items = {
                    listOf(
                        androidx.compose.foundation.ContextMenuItem("隐藏此文件夹") {
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
                        .clickable { onOpen(folder) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            folder.path.ifBlank { "本地" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${folder.trackCount} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
