package com.rabbeat.app.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.desktop.library.TrackContextMenu
import com.rabbeat.app.desktop.sys.formatMs
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EqualizerBars
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.components.StatPill
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlinx.coroutines.launch

@Composable
fun HomePage(
    db: RabBeatDatabase,
    player: PlaybackController,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onImport: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenRecent: () -> Unit,
    onOpenMostPlayed: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current

    if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp),
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(accent.color, accent.color.copy(alpha = 0.6f)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = accent.onColor,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "欢迎来到 RabBeat",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "导入本地音乐文件夹，开始管理曲库与歌词",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onImport,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.hand(),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入音乐文件夹", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        return
    }

    val albums by db.albumDao().observeAll().collectAsState(initial = emptyList())
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    val counts by db.playlistDao().observeVisibleCounts().collectAsState(initial = emptyList())
    val artworks by db.playlistDao().observeLatestArtwork().collectAsState(initial = emptyList())
    val recentDb by db.trackDao().observeRecentlyAdded(8).collectAsState(initial = emptyList())

    val countByPl = remember(counts) { counts.associate { it.playlistId to it.trackCount } }
    val artByPl = remember(artworks) { artworks.associate { it.playlistId to it.artworkUri } }

    val frequentTracks = remember(tracks) {
        tracks.sortedByDescending { it.lastPlayedAt ?: it.playCount.toLong() }
            .distinctBy { it.id }
            .take(6)
    }
    val recentTracks = remember(recentDb, tracks) {
        if (recentDb.isNotEmpty()) recentDb else tracks.sortedByDescending { it.addedAt }.take(8)
    }
    val sortedPlaylists = remember(playlists) {
        playlists.sortedWith(
            compareByDescending<PlaylistEntity> { it.isSystem }
                .thenBy { it.sortOrder }
                .thenByDescending { it.updatedAt },
        )
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "发现你的音乐",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                StatPill("共 ${tracks.size} 首 · ${albums.size} 张专辑")
            }

            if (frequentTracks.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                HomeSectionHeader("继续播放 / 最近常听", action = "更多", onAction = onOpenMostPlayed)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(frequentTracks, key = { _, t -> t.id }) { i, track ->
                        TrackContextMenu(
                            track = track,
                            db = db,
                            player = player,
                            onHide = { scope.launch { db.trackDao().setHidden(listOf(track.id), true) } },
                        ) {
                            HomeTrackSquareCard(
                                track = track,
                                current = track.id == currentTrackId,
                                onPlay = { player.playQueue(frequentTracks.map { it.toDomain(null) }, i) },
                            )
                        }
                    }
                }
            }

            if (sortedPlaylists.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                HomeSectionHeader("精选歌单", action = "全部", onAction = onOpenPlaylists)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    itemsIndexed(sortedPlaylists, key = { _, p -> p.id }) { _, pl ->
                        HomePlaylistCard(
                            playlist = UserPlaylist(
                                id = pl.id,
                                name = pl.name,
                                trackCount = countByPl[pl.id] ?: 0,
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

            if (recentTracks.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                HomeSectionHeader("最近添加", action = "查看全部", onAction = onOpenRecent)
                Spacer(Modifier.height(10.dp))
                val rows = remember(recentTracks) { recentTracks.chunked(2) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEachIndexed { rowIdx, pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            pair.forEachIndexed { colIdx, track ->
                                val idx = rowIdx * 2 + colIdx
                                Box(Modifier.weight(1f)) {
                                    TrackContextMenu(
                                        track = track,
                                        db = db,
                                        player = player,
                                        onHide = { scope.launch { db.trackDao().setHidden(listOf(track.id), true) } },
                                    ) {
                                        HomeCompactTrackRow(
                                            track = track,
                                            current = track.id == currentTrackId,
                                            isPlaying = isPlaying,
                                            onPlay = { player.playQueue(recentTracks.map { it.toDomain(null) }, idx) },
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val palette = LocalRabBeatPalette.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .hand()
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(action, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.secondaryLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeTrackSquareCard(
    track: TrackEntity,
    current: Boolean,
    onPlay: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onPlay),
    ) {
        Box(Modifier.size(140.dp).clip(RoundedCornerShape(12.dp))) {
            Artwork(track.artworkUri, track.title, Modifier.fillMaxSize(), 12.dp)
            if (hovered) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.24f)))
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = hovered,
                    enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f),
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(accent.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = accent.onColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            track.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (current) accent.color else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomePlaylistCard(
    playlist: UserPlaylist,
    onClick: () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Column(
        Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(12.dp))
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(156.dp).clip(RoundedCornerShape(12.dp))) {
            PlaylistCover(playlist = playlist, size = 156.dp, modifier = Modifier.clip(RoundedCornerShape(12.dp)))
            if (hovered) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)))
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    "${playlist.trackCount} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            playlist.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (playlist.isSystem) "系统歌单" else "${playlist.trackCount} 首歌曲",
            style = MaterialTheme.typography.bodySmall,
            color = palette.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeCompactTrackRow(
    track: TrackEntity,
    current: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val bg = when {
        current -> accent.color.copy(alpha = if (hovered) 0.12f else 0.08f)
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))) {
            Artwork(track.artworkUri, track.title, Modifier.fillMaxSize(), 8.dp)
            if (hovered || current) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (hovered) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        EqualizerBars(playing = isPlaying, tint = Color.White, height = 13.dp, barWidth = 2.5.dp)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) accent.color else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatMs(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = palette.secondaryLabel,
        )
    }
}
