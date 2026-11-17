package com.rabbeat.app.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    onReorder: ((from: Int, to: Int) -> Unit)? = null,
    onReorderStart: (() -> Unit)? = null,
    onReorderFinished: (() -> Unit)? = null,
    selecting: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelected: ((String) -> Unit)? = null,
    emptyMessage: String = "这里还没有歌曲",
    headerContent: (@Composable () -> Unit)? = null,
) {
    val likedRefs by db.playlistDao().observeRefs(LikedPlaylist.ID).collectAsState(initial = emptyList())
    val likedIds = remember(likedRefs) { likedRefs.mapTo(HashSet()) { it.trackId } }
    val liked = LocalLikedPlaylist.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dragId = remember { mutableStateOf<String?>(null) }
    val dragDy = remember { mutableStateOf(0f) }
    val dragGen = remember { mutableStateOf(0) }
    val settleJob = remember { mutableStateOf<Job?>(null) }
    val shownState = rememberUpdatedState(tracks)
    val reorderState = rememberUpdatedState(onReorder)
    val reorderStartState = rememberUpdatedState(onReorderStart)
    val reorderFinishedState = rememberUpdatedState(onReorderFinished)
    val density = LocalDensity.current
    LaunchedEffect(dragId.value) {
        val id = dragId.value ?: return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        autoScrollReorder(
            listState = listState,
            itemId = id,
            edgePx = edge,
            dragOffset = { dragDy.value },
            setDragOffset = { dragDy.value = it },
            indexOf = { key -> shownState.value.indexOfFirst { row -> row.id == key } },
            lastIndex = { shownState.value.lastIndex },
            onMove = { from, to -> reorderState.value?.invoke(from, to) },
        )
    }

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
                    val dragging = dragId.value == track.id
                    val canReorder = onReorder != null && !selecting
                    val picked = track.id in selectedIds
                    val dragModifier = if (!canReorder) {
                        Modifier
                    } else {
                        Modifier.pointerInput(track.id) {
                            reorderDrag(
                                onStart = {
                                    dragGen.value += 1
                                    settleJob.value?.cancel()
                                    reorderStartState.value?.invoke()
                                    dragId.value = track.id
                                    dragDy.value = 0f
                                },
                                onDelta = { dy ->
                                    val stride = itemStride(listState, track.id, 56.dp.toPx())
                                    dragDy.value += dy
                                    val steps = (dragDy.value / stride).toInt()
                                    if (steps == 0) return@reorderDrag
                                    val list = shownState.value
                                    val from = list.indexOfFirst { it.id == track.id }
                                    if (from < 0) return@reorderDrag
                                    val to = (from + steps).coerceIn(0, list.lastIndex)
                                    if (to == from) {
                                        dragDy.value = dragDy.value.coerceIn(-stride, stride)
                                    } else {
                                        reorderState.value?.invoke(from, to)
                                        dragDy.value -= (to - from) * stride
                                    }
                                },
                                onEnd = {
                                    val gen = dragGen.value
                                    val id = track.id
                                    reorderFinishedState.value?.invoke()
                                    settleJob.value = scope.settleDragOffset(
                                        from = dragDy.value,
                                        still = { dragGen.value == gen && dragId.value == id },
                                        write = { dragDy.value = it },
                                    ).also { job ->
                                        job.invokeOnCompletion {
                                            if (dragGen.value == gen && dragId.value == id) {
                                                dragId.value = null
                                                dragDy.value = 0f
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Box(
                        Modifier
                            .animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = if (dragging) null else ReorderSlideSpec,
                            )
                            .padding(horizontal = 24.dp)
                            .reorderLayer(dragging, dragDy.value),
                    ) {
                        val row = @Composable {
                            TrackTableRow(
                                index = i,
                                track = track,
                                current = current,
                                isPlaying = isPlaying,
                                isLiked = isLiked,
                                showAlbum = showAlbum,
                                reorderable = canReorder,
                                dragging = dragging,
                                selected = picked,
                                selecting = selecting,
                                dragModifier = dragModifier,
                                onPlay = {
                                    if (selecting) onToggleSelected?.invoke(track.id)
                                    else player.playQueue(tracks.map { it.toDomain(null) }, i)
                                },
                                onToggleLike = {
                                    scope.launch {
                                        liked?.toggle(track.id) ?: db.trackDao().toggleFavorite(track.id)
                                    }
                                },
                                onOpenAlbum = if (selecting) null else onOpenAlbum,
                            )
                        }
                        if (selecting) {
                            row()
                        } else {
                            TrackContextMenu(
                                track = track,
                                db = db,
                                player = player,
                                inPlaylistId = inPlaylistId,
                                extraMenu = extraMenuFor?.invoke(i, track) ?: emptyList(),
                                onHide = { scope.launch { db.trackDao().setHidden(listOf(track.id), true) } },
                            ) { row() }
                        }
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
        )

        val currentIndex = tracks.indexOfFirst { it.id == currentTrackId }
        val itemOffset = (if (headerContent != null) 1 else 0) + 1
        val currentVisible by remember(currentTrackId) {
            derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == currentTrackId } }
        }
        var recentlyScrolled by remember { mutableStateOf(false) }
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                recentlyScrolled = true
            } else {
                delay(2500)
                recentlyScrolled = false
            }
        }
        AnimatedVisibility(
            visible = currentIndex >= 0 && recentlyScrolled && !currentVisible,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 14.dp),
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.85f),
        ) {
            Surface(
                onClick = { scope.launch { listState.animateScrollToItem(currentIndex + itemOffset) } },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.hand(),
            ) {
                Box(Modifier.padding(9.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "定位到当前播放",
                        tint = LocalAccentPalette.current.color,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
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
    reorderable: Boolean,
    dragging: Boolean,
    selected: Boolean = false,
    selecting: Boolean = false,
    dragModifier: Modifier,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenAlbum: ((String) -> Unit)?,
) {
    val accent = LocalAccentPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val bg = when {
        dragging -> MaterialTheme.colorScheme.surfaceContainerHigh
        selected -> accent.color.copy(alpha = if (hovered) 0.16f else 0.10f)
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
        Box(
            Modifier.width(44.dp).fillMaxHeight().then(dragModifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                selecting -> Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (selected) accent.color else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (selected) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            RoundedCornerShape(5.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已选择",
                            tint = accent.onColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                reorderable && (hovered || dragging) -> Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "拖动排序",
                    tint = if (current) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                hovered -> Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = if (current) accent.color else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
                current -> EqualizerBars(playing = isPlaying, height = 13.dp, barWidth = 2.5.dp)
                else -> Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
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
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal val ReorderSlideSpec: FiniteAnimationSpec<IntOffset> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

internal fun Modifier.reorderLayer(dragging: Boolean, translationY: Float): Modifier =
    zIndex(if (dragging) 1f else 0f).graphicsLayer {
        this.translationY = if (dragging) translationY else 0f
        val lift = if (dragging) 1f else 0f
        scaleX = 1f + 0.012f * lift
        scaleY = 1f + 0.012f * lift
        shadowElevation = 8.dp.toPx() * lift
        shape = RoundedCornerShape(10.dp)
        clip = false
    }

internal fun itemStride(listState: LazyListState, itemId: String, fallback: Float): Float {
    val measured = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemId }?.size?.toFloat()
    return if (measured != null && measured > 1f) measured else fallback
}

/** Keeps the dragged row under the pointer while the list slides and neighbors trade places. */
internal suspend fun CoroutineScope.autoScrollReorder(
    listState: LazyListState,
    itemId: String,
    edgePx: Float,
    dragOffset: () -> Float,
    setDragOffset: (Float) -> Unit,
    indexOf: (String) -> Int,
    lastIndex: () -> Int,
    onMove: (from: Int, to: Int) -> Unit,
) {
    var scrolledSinceSwap = 0f
    while (isActive) {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == itemId }
        if (item == null) {
            delay(32)
            continue
        }
        val visualTop = item.offset + dragOffset()
        val visualBottom = visualTop + item.size
        val speed = when {
            visualTop < edgePx -> -480f
            visualBottom > info.viewportEndOffset - edgePx -> 480f
            else -> 0f
        }
        if (speed == 0f) {
            scrolledSinceSwap = 0f
            delay(32)
            continue
        }
        val consumed = listState.scrollBy(speed * 0.016f)
        setDragOffset(dragOffset() + consumed)
        scrolledSinceSwap += consumed
        val stride = item.size.toFloat().coerceAtLeast(1f)
        val steps = (scrolledSinceSwap / stride).toInt()
        if (steps != 0) {
            val from = indexOf(itemId)
            val to = (from + steps).coerceIn(0, lastIndex())
            if (from >= 0 && to != from) {
                onMove(from, to)
                val moved = to - from
                setDragOffset(dragOffset() - moved * stride)
                scrolledSinceSwap -= moved * stride
            } else {
                scrolledSinceSwap = scrolledSinceSwap.coerceIn(-stride, stride)
            }
        }
        delay(16)
    }
}

internal fun CoroutineScope.settleDragOffset(
    from: Float,
    still: () -> Boolean,
    write: (Float) -> Unit,
): Job = launch {
    if (!still()) return@launch
    if (abs(from) < 0.5f) {
        if (still()) write(0f)
        return@launch
    }
    val anim = Animatable(from)
    anim.animateTo(
        0f,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
    ) {
        if (still()) write(value)
    }
    if (still()) write(0f)
}

/** Claims the pointer before the list scroll, then reports vertical movement. */
internal suspend fun androidx.compose.ui.input.pointer.PointerInputScope.reorderDrag(
    onStart: () -> Unit,
    onDelta: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        down.consume()
        val pointerId = down.id
        var total = 0f
        var started = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                val dy = change.positionChange().y
                change.consume()
                if (!started) {
                    total += dy
                    if (abs(total) < viewConfiguration.touchSlop) continue
                    started = true
                    onStart()
                    onDelta(total)
                } else {
                    onDelta(dy)
                }
            }
        } finally {
            if (started) onEnd()
        }
    }
}
