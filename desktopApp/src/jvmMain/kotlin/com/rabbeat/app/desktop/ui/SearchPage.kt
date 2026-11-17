package com.rabbeat.app.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class SearchMatches(
    val tracks: List<TrackEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
)

@Composable
fun SearchPage(
    db: RabBeatDatabase,
    player: PlaybackController,
    tracks: List<TrackEntity>,
    query: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val albums by db.albumDao().observeAll().collectAsState(initial = emptyList())
    val artists by db.artistDao().observeAll().collectAsState(initial = emptyList())
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    val artworks by db.playlistDao().observeLatestArtwork().collectAsState(initial = emptyList())
    val artByPl = remember(artworks) { artworks.associate { it.playlistId to it.artworkUri } }

    val matches by produceState(SearchMatches(), query, tracks, albums, artists, playlists) {
        value = withContext(Dispatchers.Default) {
            val q = query.trim()
            if (q.isEmpty()) SearchMatches()
            else SearchMatches(
                tracks = tracks.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
                },
                albums = albums.filter {
                    it.title.contains(q, true) || it.artist.contains(q, true)
                },
                artists = artists.filter {
                    it.name.contains(q, true)
                },
                playlists = playlists.filter {
                    it.name.contains(q, true)
                },
            )
        }
    }

    var filterTab by remember { mutableStateOf(0) }
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val fallbackArtistArt = remember(albums) {
        albums.filter { !it.artworkUri.isNullOrBlank() }.associate { it.artist to it.artworkUri }
    }
    val totalCount = matches.tracks.size + matches.albums.size + matches.artists.size + matches.playlists.size

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (query.isBlank()) "搜索" else "搜索 “$query”",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "共 $totalCount 条结果",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                )
            }
            Spacer(Modifier.height(12.dp))
            val tabs = listOf(
                "全部",
                "单曲 (${matches.tracks.size})",
                "专辑 (${matches.albums.size})",
                "艺人 (${matches.artists.size})",
                "歌单 (${matches.playlists.size})",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { idx, label ->
                    val selected = filterTab == idx
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (selected) accent.soft else palette.fill)
                            .hand()
                            .clickable { filterTab = idx }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) accent.color else palette.secondaryLabel,
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

        if (totalCount == 0) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    message = if (query.isBlank()) "在顶部搜索框输入歌名、歌手或专辑" else "未找到与 “$query” 相关的内容",
                )
            }
            return@Column
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (filterTab) {
                0 -> TrackTable(
                    db = db,
                    player = player,
                    tracks = matches.tracks,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onOpenAlbum = onOpenAlbum,
                    emptyMessage = "没有匹配的歌曲",
                    headerContent = {
                        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            if (matches.albums.isNotEmpty()) {
                                Text(
                                    "专辑",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 10.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(matches.albums.take(6), key = { it.id }) { album ->
                                        SearchAlbumCard(album) { onOpenAlbum(album.id) }
                                    }
                                }
                            }
                            if (matches.artists.isNotEmpty()) {
                                Text(
                                    "艺人",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 10.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(matches.artists.take(8), key = { it.id }) { artist ->
                                        SearchArtistCard(
                                            artist = artist,
                                            avatarUri = artist.artworkUri ?: fallbackArtistArt[artist.name],
                                            onClick = { onOpenArtist(artist.id) },
                                        )
                                    }
                                }
                            }
                            if (matches.playlists.isNotEmpty()) {
                                Text(
                                    "歌单",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 28.dp, top = 18.dp, bottom = 10.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 28.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    items(matches.playlists.take(6), key = { it.id }) { pl ->
                                        SearchPlaylistCard(
                                            playlist = UserPlaylist(
                                                id = pl.id,
                                                name = pl.name,
                                                trackCount = 0,
                                                updatedAt = pl.updatedAt,
                                                isSystem = pl.isSystem,
                                                coverUri = pl.coverUri,
                                                autoCoverUri = artByPl[pl.id],
                                            ),
                                            onClick = { onOpenPlaylist(pl.id) },
                                        )
                                    }
                                }
                            }
                            if (matches.tracks.isNotEmpty()) {
                                Text(
                                    "单曲",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 4.dp),
                                )
                            }
                        }
                    },
                )
                1 -> TrackTable(
                    db = db,
                    player = player,
                    tracks = matches.tracks,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onOpenAlbum = onOpenAlbum,
                    emptyMessage = "没有匹配的单曲",
                )
                2 -> {
                    if (matches.albums.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(message = "没有匹配的专辑")
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(168.dp),
                                state = gridState,
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(matches.albums, key = { it.id }) { album ->
                                    SearchAlbumGridCard(album) { onOpenAlbum(album.id) }
                                }
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(gridState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
                            )
                        }
                    }
                }
                3 -> {
                    if (matches.artists.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(message = "没有匹配的艺人")
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(152.dp),
                                state = gridState,
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(matches.artists, key = { it.id }) { artist ->
                                    SearchArtistCard(
                                        artist = artist,
                                        avatarUri = artist.artworkUri ?: fallbackArtistArt[artist.name],
                                        onClick = { onOpenArtist(artist.id) },
                                    )
                                }
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(gridState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
                            )
                        }
                    }
                }
                else -> {
                    if (matches.playlists.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(message = "没有匹配的歌单")
                        }
                    } else {
                        val gridState = rememberLazyGridState()
                        Box(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(168.dp),
                                state = gridState,
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(matches.playlists, key = { it.id }) { pl ->
                                    SearchPlaylistCard(
                                        playlist = UserPlaylist(
                                            id = pl.id,
                                            name = pl.name,
                                            trackCount = 0,
                                            updatedAt = pl.updatedAt,
                                            isSystem = pl.isSystem,
                                            coverUri = pl.coverUri,
                                            autoCoverUri = artByPl[pl.id],
                                        ),
                                        onClick = { onOpenPlaylist(pl.id) },
                                    )
                                }
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(gridState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAlbumCard(album: AlbumEntity, onClick: () -> Unit) {
    val palette = LocalRabBeatPalette.current
    Column(
        Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .hand()
            .clickable(onClick = onClick),
    ) {
        Artwork(album.artworkUri, album.title, Modifier.size(136.dp), 12.dp)
        Spacer(Modifier.height(6.dp))
        Text(album.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(album.artist, fontSize = 12.sp, color = palette.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchAlbumGridCard(album: AlbumEntity, onClick: () -> Unit) {
    val palette = LocalRabBeatPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hand()
            .clickable(onClick = onClick),
    ) {
        Artwork(album.artworkUri, album.title, Modifier.fillMaxWidth().aspectRatio(1f), 12.dp)
        Spacer(Modifier.height(8.dp))
        Text(album.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${album.artist} · ${album.trackCount} 首", fontSize = 12.sp, color = palette.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchArtistCard(artist: ArtistEntity, avatarUri: String?, onClick: () -> Unit) {
    val palette = LocalRabBeatPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Column(
        Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) else Color.Transparent)
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(avatarUri, artist.name, Modifier.size(88.dp).clip(CircleShape), 44.dp)
        Spacer(Modifier.height(8.dp))
        Text(artist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("${artist.albumCount} 专辑", fontSize = 11.sp, color = palette.secondaryLabel, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchPlaylistCard(playlist: UserPlaylist, onClick: () -> Unit) {
    Column(
        Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .hand()
            .clickable(onClick = onClick),
    ) {
        PlaylistCover(playlist = playlist, size = 136.dp, modifier = Modifier.clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.height(6.dp))
        Text(playlist.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
