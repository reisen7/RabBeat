package com.rabbeat.app.desktop.ui
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.ui.components.LocalCardOpacity
import com.rabbeat.app.ui.components.LocalHasBackgroundImage
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.theme.LocalAccentPalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun Sidebar(
    db: RabBeatDatabase,
    route: DesktopRoute,
    onTopLevel: (DesktopRoute) -> Unit,
    onOpenLiked: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccentPalette.current
    val surface = translucentSurface()
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    val artworks by db.playlistDao().observeLatestArtwork().collectAsState(initial = emptyList())
    val artByPlaylist = remember(artworks) { artworks.associate { it.playlistId to it.artworkUri } }
    val userPlaylists = playlists.filter { !it.isSystem }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val orderState = remember { mutableStateOf<List<String>?>(null) }
    val draggingId = remember { mutableStateOf<String?>(null) }
    val dragDy = remember { mutableStateOf(0f) }
    val dragGen = remember { mutableStateOf(0) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val shown = remember(userPlaylists, orderState.value) {
        val ids = orderState.value ?: return@remember userPlaylists
        val byId = userPlaylists.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        val extra = userPlaylists.filter { it.id !in ids.toSet() }
        ordered + extra
    }
    val shownState = rememberUpdatedState(shown)
    val listState = rememberLazyListState()
    LaunchedEffect(userPlaylists) {
        val visible = userPlaylists.map { it.id }
        if (draggingId.value == null && orderState.value != null && visible == orderState.value) {
            orderState.value = null
        }
    }
    LaunchedEffect(draggingId.value) {
        val id = draggingId.value ?: return@LaunchedEffect
        val edge = with(density) { 36.dp.toPx() }
        autoScrollReorder(
            listState = listState,
            itemId = id,
            edgePx = edge,
            dragOffset = { dragDy.value },
            setDragOffset = { dragDy.value = it },
            indexOf = { key ->
                (orderState.value ?: shownState.value.map { it.id }).indexOf(key)
            },
            lastIndex = {
                (orderState.value ?: shownState.value.map { it.id }).lastIndex
            },
            onMove = { from, to ->
                val ids = (orderState.value ?: shownState.value.map { it.id }).toMutableList()
                if (from in ids.indices && to in ids.indices && from != to) {
                    ids.add(to, ids.removeAt(from))
                    orderState.value = ids.toList()
                }
            },
        )
    }

    Column(modifier.width(220.dp).fillMaxHeight().background(surface)) {
        Text(
            "RabBeat",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 18.dp),
        )
        NavGroupTitle("我的音乐")
        NavItem(Icons.Default.Home, "首页", route is DesktopRoute.Home) {
            onTopLevel(DesktopRoute.Home)
        }
        NavItem(Icons.Default.LibraryMusic, "本地音乐", route is DesktopRoute.Library) {
            onTopLevel(DesktopRoute.Library)
        }
        NavItem(Icons.Default.History, "最近添加", route is DesktopRoute.RecentAdded) {
            onTopLevel(DesktopRoute.RecentAdded)
        }
        NavItem(Icons.AutoMirrored.Filled.TrendingUp, "最常播放", route is DesktopRoute.MostPlayed) {
            onTopLevel(DesktopRoute.MostPlayed)
        }
        NavItem(
            Icons.Default.Favorite, "我喜欢",
            (route as? DesktopRoute.PlaylistDetail)?.playlistId == LikedPlaylist.ID,
        ) { onOpenLiked() }
        NavItem(Icons.Default.VisibilityOff, "已隐藏", route is DesktopRoute.HiddenSongs) {
            onTopLevel(DesktopRoute.HiddenSongs)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavGroupTitle("创建的歌单", Modifier.weight(1f))
            IconButton(
                onClick = { onTopLevel(DesktopRoute.Playlists) },
                modifier = Modifier.padding(end = 10.dp).size(28.dp).hand(),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "歌单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        ) {
            itemsIndexed(shown, key = { _, it -> it.id }) { _, pl ->
                val interactionSource = remember { MutableInteractionSource() }
                val hovered by interactionSource.collectIsHoveredAsState()
                val selected = (route as? DesktopRoute.PlaylistDetail)?.playlistId == pl.id
                val dragging = draggingId.value == pl.id
                Row(
                    Modifier
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null,
                            placementSpec = if (dragging) null else ReorderSlideSpec,
                        )
                        .reorderLayer(dragging, dragDy.value)
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                dragging -> MaterialTheme.colorScheme.surfaceContainerHigh
                                selected -> accent.color.copy(alpha = 0.12f)
                                hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .hoverable(interactionSource)
                        .hand()
                        .clickable { onOpenPlaylist(pl.id) }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 40.dp)
                            .pointerInput(pl.id) {
                                reorderDrag(
                                    onStart = {
                                        dragGen.value += 1
                                        settleJob.value?.cancel()
                                        if (orderState.value == null) {
                                            orderState.value = shownState.value.map { it.id }
                                        }
                                        draggingId.value = pl.id
                                        dragDy.value = 0f
                                    },
                                    onDelta = { dy ->
                                        val stride = itemStride(listState, pl.id, 40.dp.toPx())
                                        dragDy.value += dy
                                        val steps = (dragDy.value / stride).toInt()
                                        if (steps == 0) return@reorderDrag
                                        val ids = (orderState.value ?: shownState.value.map { it.id }).toMutableList()
                                        val from = ids.indexOf(pl.id)
                                        if (from < 0) return@reorderDrag
                                        val to = (from + steps).coerceIn(0, ids.lastIndex)
                                        if (to == from) {
                                            dragDy.value = dragDy.value.coerceIn(-stride, stride)
                                        } else {
                                            ids.add(to, ids.removeAt(from))
                                            orderState.value = ids.toList()
                                            dragDy.value -= (to - from) * stride
                                        }
                                    },
                                    onEnd = {
                                        val gen = dragGen.value
                                        val id = pl.id
                                        val ids = orderState.value
                                        if (ids != null) {
                                            scope.launch {
                                                ids.forEachIndexed { index, playlistId ->
                                                    db.playlistDao().setSortOrder(playlistId, index)
                                                }
                                                DesktopSettings().notePlaylistOrder()
                                            }
                                        }
                                        settleJob.value = scope.settleDragOffset(
                                            from = dragDy.value,
                                            still = { dragGen.value == gen && draggingId.value == id },
                                            write = { dragDy.value = it },
                                        ).also { job ->
                                            job.invokeOnCompletion {
                                                if (dragGen.value == gen && draggingId.value == id) {
                                                    draggingId.value = null
                                                    dragDy.value = 0f
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "拖动排序",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (hovered || dragging) 0.9f else 0.35f,
                            ),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    PlaylistCover(
                        UserPlaylist(
                            id = pl.id, name = pl.name, trackCount = 0,
                            updatedAt = pl.updatedAt, isSystem = pl.isSystem,
                            coverUri = pl.coverUri, autoCoverUri = artByPlaylist[pl.id],
                        ),
                        size = 28.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        pl.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) accent.color else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        NavItem(Icons.Default.Settings, "设置", route is DesktopRoute.Settings) {
            onTopLevel(DesktopRoute.Settings)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
internal fun translucentSurface(): androidx.compose.ui.graphics.Color {
    val hasBg = LocalHasBackgroundImage.current
    val alpha = LocalCardOpacity.current.coerceIn(0f, 1f)
    return MaterialTheme.colorScheme.surface.copy(alpha = if (hasBg) alpha else 1f)
}

@Composable
private fun NavGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> accent.color.copy(alpha = 0.12f)
                    hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) accent.color else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
