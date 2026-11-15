package com.rabbeat.app.desktop

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
import androidx.compose.material3.Icon
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
    val pager = rememberPagerState(pageCount = { TABS.size })
    val scope = rememberCoroutineScope()

    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PillTabRow(
            tabs = TABS,
            selectedIndex = tab,
            onSelected = {
                tab = it
                scope.launch { pager.animateScrollToPage(it) }
            },
        )
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
                else -> FolderList(folders) { folder -> onOpenFolder(folder.path) }
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
private fun FolderList(folders: List<FolderSummary>, onOpen: (FolderSummary) -> Unit) {
    if (folders.isEmpty()) {
        EmptyState(message = "没有文件夹，导入音乐后按目录分组")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
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
