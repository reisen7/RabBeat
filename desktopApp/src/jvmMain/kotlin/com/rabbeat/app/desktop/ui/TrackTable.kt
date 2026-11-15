package com.rabbeat.app.desktop.ui

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.desktop.library.LocalLikedPlaylist
import com.rabbeat.app.desktop.library.TrackContextMenu
import com.rabbeat.app.desktop.sys.formatMs
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.EqualizerBars
import com.rabbeat.app.ui.theme.LocalAccentPalette
import kotlinx.coroutines.launch

@Composable
fun TrackTable(
    db: RabBeatDatabase,
    player: PlaybackController,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    inPlaylistId: String? = null,
    sort: TrackSort? = null,
    ascending: Boolean = true,
    onSortChange: ((TrackSort) -> Unit)? = null,
    onOpenAlbum: ((String) -> Unit)? = null,
    extraMenuFor: ((Int, TrackEntity) -> List<ContextMenuItem>)? = null,
    emptyMessage: String = "这里还没有歌曲",
    headerContent: (@Composable () -> Unit)? = null,
) {
    val likedRefs by db.playlistDao().observeRefs(LikedPlaylist.ID).collectAsState(initial = emptyList())
    val likedIds = remember(likedRefs) { likedRefs.mapTo(HashSet()) { it.trackId } }
    val liked = LocalLikedPlaylist.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (headerContent != null) {
                item(key = "__table_hero__") {
                    headerContent()
                }
            }
            if (tracks.isEmpty()) {
                item(key = "__table_empty__") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(message = emptyMessage)
                    }
                }
            } else {
                item(key = "__table_columns__") {
                    TrackTableHeader(
                        showAlbum = showAlbum,
                        sort = sort,
                        ascending = ascending,
                        onSortChange = onSortChange,
                    )
                }
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                    val current = track.id == currentTrackId
                    val isLiked = track.id in likedIds
                    Box(Modifier.padding(horizontal = 24.dp)) {
                        TrackContextMenu(
                            track = track,
                            db = db,
                            player = player,
                            inPlaylistId = inPlaylistId,
                            extraMenu = extraMenuFor?.invoke(i, track) ?: emptyList(),
                            onHide = { scope.launch { db.trackDao().setHidden(listOf(track.id), true) } },
                        ) {
                            TrackTableRow(
                                index = i,
                                track = track,
                                current = current,
                                isPlaying = isPlaying,
                                isLiked = isLiked,
                                showAlbum = showAlbum,
                                onPlay = { player.playQueue(tracks.map { it.toDomain(null) }, i) },
                                onToggleLike = {
                                    scope.launch {
                                        liked?.toggle(track.id) ?: db.trackDao().toggleFavorite(track.id)
                                    }
                                },
                                onOpenAlbum = onOpenAlbum,
                            )
                        }
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

@Composable
private fun TrackTableHeader(
    showAlbum: Boolean,
    sort: TrackSort?,
    ascending: Boolean,
    onSortChange: ((TrackSort) -> Unit)?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                Text("#", style = MaterialTheme.typography.labelSmall, color = muted)
            }
            Spacer(Modifier.width(8.dp))
            SortableColumnHeader(
                label = "标题",
                active = sort == TrackSort.TITLE,
                ascending = ascending,
                clickable = onSortChange != null,
                onClick = { onSortChange?.invoke(TrackSort.TITLE) },
                modifier = Modifier.weight(2.2f),
            )
            if (showAlbum) {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1.4f), contentAlignment = Alignment.CenterStart) {
                    Text("专辑", style = MaterialTheme.typography.labelSmall, color = muted)
                }
            }
            Spacer(Modifier.width(44.dp))
            Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
                SortableColumnHeader(
                    label = "时长",
                    active = sort == TrackSort.DURATION,
                    ascending = ascending,
                    clickable = onSortChange != null,
                    onClick = { onSortChange?.invoke(TrackSort.DURATION) },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SortableColumnHeader(
    label: String,
    active: Boolean,
    ascending: Boolean,
    clickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentPalette.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val color = if (active) accent.color else muted
    Row(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .then(if (clickable) Modifier.hand().clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
        )
        if (active) {
            Spacer(Modifier.width(3.dp))
            Icon(
                if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun TrackTableRow(
    index: Int,
    track: TrackEntity,
    current: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    showAlbum: Boolean,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenAlbum: ((String) -> Unit)?,
) {
    val accent = LocalAccentPalette.current
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
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            when {
                hovered -> Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = if (current) accent.color else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                current -> EqualizerBars(playing = isPlaying, height = 13.dp, barWidth = 2.5.dp)
                else -> Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(2.2f), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                artworkUri = track.artworkUri,
                title = track.title,
                modifier = Modifier.size(40.dp),
                cornerRadius = 8.dp,
            )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showAlbum) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1.4f), contentAlignment = Alignment.CenterStart) {
                val albumId = track.albumId
                val clickableAlbum = onOpenAlbum != null && albumId != null
                Text(
                    track.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (clickableAlbum) {
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .hand()
                            .clickable { onOpenAlbum(albumId) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    },
                )
            }
        }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            if (hovered || isLiked) {
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(32.dp).hand(),
                ) {
                    Icon(
                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "取消喜欢" else "喜欢",
                        tint = if (isLiked) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Box(Modifier.width(76.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                formatMs(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
