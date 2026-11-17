package com.rabbeat.app.desktop.playlist

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.desktop.library.DetailHeroHeader
import com.rabbeat.app.desktop.library.LocalLikedPlaylist
import com.rabbeat.app.desktop.library.PlaylistPickerDialog
import com.rabbeat.app.desktop.sys.DesktopSettings
import com.rabbeat.app.desktop.library.buildMetaLine
import com.rabbeat.app.desktop.net.NeteaseImporter
import com.rabbeat.app.desktop.ui.ImageCropDialog
import com.rabbeat.app.desktop.ui.LocalBriefNotice
import com.rabbeat.app.desktop.ui.TrackTable
import com.rabbeat.app.desktop.ui.hand
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import com.rabbeat.app.desktop.sys.pickFile
import kotlinx.coroutines.launch

@Composable
fun PlaylistListView(
    db: RabBeatDatabase,
    player: PlaybackController,
    onOpen: (String) -> Unit,
) {
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    val counts by db.playlistDao().observeVisibleCounts().collectAsState(initial = emptyList())
    val artworks by db.playlistDao().observeLatestArtwork().collectAsState(initial = emptyList())
    val allTracks by db.trackDao().observeAll().collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val importer = remember { NeteaseImporter(db) }
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val tracksById = remember(allTracks) { allTracks.associateBy { it.id } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "我的歌单",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "${playlists.size} 个歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryLabel,
            )
            Spacer(Modifier.weight(1f))
            importResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
            }
            OutlinedButton(
                onClick = { importing = true },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp).hand(),
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("导入网易云歌单")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = { creating = true },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.color,
                    contentColor = accent.onColor,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.height(36.dp).hand(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建歌单", fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        )

        if (playlists.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(message = "还没有歌单")
            }
        } else {
            val gridState = rememberLazyGridState()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(176.dp),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val trackCount = counts.firstOrNull { it.playlistId == playlist.id }?.trackCount ?: 0
                        val coverUri = playlist.coverUri
                            ?: artworks.firstOrNull { it.playlistId == playlist.id }?.artworkUri
                        val uiPlaylist = UserPlaylist(
                            id = playlist.id,
                            name = playlist.name,
                            trackCount = trackCount,
                            updatedAt = playlist.updatedAt,
                            isSystem = playlist.isSystem,
                            coverUri = playlist.coverUri,
                            autoCoverUri = coverUri,
                        )
                        val interactionSource = remember { MutableInteractionSource() }
                        val hovered by interactionSource.collectIsHoveredAsState()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .hoverable(interactionSource)
                                .hand()
                                .clickable { onOpen(playlist.id) },
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                                if (uiPlaylist.displayCoverUri != null) {
                                    Artwork(
                                        artworkUri = uiPlaylist.displayCoverUri,
                                        title = playlist.name,
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 12.dp,
                                    )
                                } else {
                                    PlaylistCover(
                                        playlist = uiPlaylist,
                                        size = 200.dp,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                    )
                                }
                                if (hovered) {
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
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
                                        "$trackCount 首",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = hovered && trackCount > 0,
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
                                                    scope.launch {
                                                        val refs = db.playlistDao().refs(playlist.id)
                                                        val list = refs.mapNotNull { tracksById[it.trackId]?.toDomain(null) }
                                                        if (list.isNotEmpty()) player.playQueue(list, 0)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "播放歌单",
                                                tint = accent.onColor,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
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
                                if (playlist.isSystem) "我喜欢 · $trackCount 首" else "$trackCount 首歌曲",
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
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
                )
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建歌单", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("歌单名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    modifier = Modifier.hand(),
                    onClick = {
                        creating = false
                        scope.launch {
                            db.playlistDao().upsert(
                                PlaylistEntity(
                                    id = "dpl_" + UUID.randomUUID().toString().take(12),
                                    name = name.trim(),
                                    isSystem = false,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                ),
                            )
                            DesktopSettings().notePlaylistOrder()
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }, modifier = Modifier.hand()) { Text("取消") }
            },
        )
    }

    if (importing) {
        var link by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (busy == null) importing = false },
            title = { Text("导入网易云歌单", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        singleLine = true,
                        placeholder = { Text("歌单链接或 ID") },
                        enabled = busy == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    busy?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryLabel,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = link.isNotBlank() && busy == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    modifier = Modifier.hand(),
                    onClick = {
                        scope.launch {
                            busy = "解析中…"
                            runCatching {
                                importer.import(link) { d, t -> busy = "导入中 $d/$t" }
                            }.onSuccess { r ->
                                importResult = "「${r.playlistName}」导入 ${r.imported} 首" +
                                    (if (r.matched > 0) "，匹配本地 ${r.matched}" else "") +
                                    (if (r.skipped > 0) "，跳过 ${r.skipped}" else "")
                                importing = false
                            }.onFailure { e ->
                                busy = null
                                importResult = e.message ?: "导入失败"
                                importing = false
                            }
                        }
                    },
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(enabled = busy == null, onClick = { importing = false }, modifier = Modifier.hand()) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
fun PlaylistDetailView(
    db: RabBeatDatabase,
    player: PlaybackController,
    playlistId: String,
    currentTrackId: String?,
    isPlaying: Boolean,
    onOpenAlbum: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val refs by db.playlistDao().observeRefs(playlistId).collectAsState(initial = emptyList())
    val all by db.trackDao().observeAll().collectAsState(initial = emptyList())
    var playlistTick by remember { mutableStateOf(0) }
    var playlist by remember { mutableStateOf<PlaylistEntity?>(null) }
    LaunchedEffect(playlistId, playlistTick) {
        playlist = db.playlistDao().get(playlistId)
    }
    val scope = rememberCoroutineScope()
    val saveMutex = remember(playlistId) { Mutex() }
    val byId = remember(all) { all.associateBy { it.id } }
    val orderState = remember(playlistId) { mutableStateOf<List<String>?>(null) }
    val draggingState = remember(playlistId) { mutableStateOf(false) }
    val localIds = orderState.value
    val dragging = draggingState.value
    val baseTracks = remember(refs, byId) { refs.mapNotNull { byId[it.trackId] } }
    val tracks = remember(baseTracks, localIds) {
        val ids = localIds
        if (ids == null) baseTracks else ids.mapNotNull { id -> baseTracks.find { it.id == id } }
    }
    LaunchedEffect(refs, byId, dragging) {
        if (draggingState.value || orderState.value == null) return@LaunchedEffect
        val visible = refs.map { it.trackId }.filter { it in byId }
        if (visible == orderState.value) orderState.value = null
    }
    val tracksState = rememberUpdatedState(tracks)
    fun moveTrack(from: Int, to: Int) {
        val ids = (orderState.value ?: tracksState.value.map { it.id }).toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) return
        ids.add(to, ids.removeAt(from))
        orderState.value = ids.toList()
    }
    fun persistOrder(ids: List<String>) {
        scope.launch { saveMutex.withLock { savePlaylistOrder(db, playlistId, ids) } }
    }
    var renaming by remember { mutableStateOf(false) }
    var coverCropSource by remember { mutableStateOf<File?>(null) }
    var selecting by remember(playlistId) { mutableStateOf(false) }
    var selected by remember(playlistId) { mutableStateOf(emptySet<String>()) }
    var confirmRemove by remember { mutableStateOf(false) }
    var pickingPlaylist by remember { mutableStateOf(false) }
    val notify = LocalBriefNotice.current
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val liked = LocalLikedPlaylist.current
    LaunchedEffect(tracks) {
        val ids = tracks.mapTo(HashSet()) { it.id }
        selected = selected.filterTo(HashSet()) { it in ids }
    }
    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    Column(Modifier.fillMaxSize()) {
    TrackTable(
        db = db,
        player = player,
        tracks = tracks,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        modifier = Modifier.weight(1f),
        inPlaylistId = playlistId,
        onOpenAlbum = onOpenAlbum,
        selecting = selecting,
        selectedIds = selected,
        onToggleSelected = { id ->
            selected = if (id in selected) selected - id else selected + id
        },
        onReorder = if (selecting) null else { from, to -> moveTrack(from, to) },
        onReorderStart = {
            draggingState.value = true
            if (orderState.value == null) orderState.value = tracksState.value.map { it.id }
        },
        onReorderFinished = {
            draggingState.value = false
            orderState.value?.let { persistOrder(it) }
        },
        extraMenuFor = { i, _ ->
            buildList {
                if (i > 0) {
                    add(ContextMenuItem("上移") {
                        moveTrack(i, i - 1)
                        orderState.value?.let { persistOrder(it) }
                    })
                }
                if (i < tracks.lastIndex) {
                    add(ContextMenuItem("下移") {
                        moveTrack(i, i + 1)
                        orderState.value?.let { persistOrder(it) }
                    })
                }
            }
        },
        emptyMessage = "歌单还是空的，右键歌曲可加入歌单",
        headerContent = {
            val autoCover = refs
                .sortedBy { it.position }
                .firstNotNullOfOrNull { ref -> byId[ref.trackId]?.artworkUri?.takeIf { it.isNotBlank() } }
            val uiPlaylist = UserPlaylist(
                id = playlistId,
                name = playlist?.name ?: "歌单",
                trackCount = tracks.size,
                updatedAt = playlist?.updatedAt ?: 0L,
                isSystem = playlist?.isSystem == true,
                coverUri = playlist?.coverUri,
                autoCoverUri = autoCover,
            )
            DetailHeroHeader(
                badge = if (playlist?.isSystem == true) "我喜欢" else "歌单",
                title = playlist?.name ?: "歌单",
                metaLine = if (selecting) "已选择 ${selected.size} 首" else buildMetaLine(null, tracks),
                tracks = tracks,
                player = player,
                onBack = onBack,
                showBackAndBadge = false,
                cover = {
                    val shape = RoundedCornerShape(14.dp)
                    val plain = uiPlaylist.displayCoverUri == null
                    val frame = if (plain) {
                        Modifier.size(168.dp).clip(shape)
                    } else {
                        Modifier.size(168.dp).shadow(12.dp, shape).clip(shape)
                    }
                    Box(frame) {
                        if (uiPlaylist.displayCoverUri != null) {
                            Artwork(uiPlaylist.displayCoverUri, uiPlaylist.name, Modifier.fillMaxSize(), 14.dp)
                        } else {
                            PlaylistCover(uiPlaylist, size = 168.dp, modifier = Modifier.fillMaxSize(), corner = 14.dp)
                        }
                    }
                },
                extraActions = {
                    if (selecting) {
                        val allPicked = tracks.isNotEmpty() && tracks.all { it.id in selected }
                        OutlinedButton(
                            onClick = {
                                selected = if (allPicked) emptySet() else tracks.mapTo(HashSet()) { it.id }
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp).hand(),
                        ) { Text(if (allPicked) "取消全选" else "全选") }
                        OutlinedButton(
                            onClick = { exitSelection() },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp).hand(),
                        ) { Text("完成") }
                    } else {
                    OutlinedButton(
                        onClick = { selecting = true },
                        enabled = tracks.isNotEmpty(),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp).hand(),
                    ) {
                        Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("多选")
                    }
                    HeroCircleAction(Icons.Default.Image, "设置封面") {
                        scope.launch {
                            pickFile("选择图片", "jpg", "jpeg", "png", "webp")?.let { file ->
                                coverCropSource = file
                            }
                        }
                    }
                    if (playlist?.isSystem == false) {
                        HeroCircleAction(Icons.Default.Edit, "重命名") { renaming = true }
                        HeroCircleAction(Icons.Default.Delete, "删除歌单") {
                            scope.launch {
                                db.playlistDao().deleteTracks(playlistId)
                                db.playlistDao().delete(playlistId)
                                DesktopSettings().notePlaylistOrder()
                                onBack()
                            }
                        }
                    }
                    }
                },
            )
        },
    )
        if (selecting) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.separator),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "已选择 ${selected.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        val picked = tracks.filter { it.id in selected }.map { it.toDomain(null) }
                        if (picked.isNotEmpty()) player.playNext(picked)
                        exitSelection()
                    },
                    enabled = selected.isNotEmpty(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp).hand(),
                ) { Text("下一首播放") }
                OutlinedButton(
                    onClick = { pickingPlaylist = true },
                    enabled = selected.isNotEmpty(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp).hand(),
                ) { Text("添加到歌单") }
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    enabled = selected.isNotEmpty(),
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF3B30)),
                    modifier = Modifier.height(36.dp).hand(),
                ) { Text("从歌单移除") }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("从歌单删除 ${selected.size} 首歌？", fontWeight = FontWeight.SemiBold) },
            text = { Text("歌曲文件不会被删除") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = selected.toList()
                        confirmRemove = false
                        exitSelection()
                        scope.launch {
                            ids.chunked(400).forEach { db.playlistDao().removeTracksIn(playlistId, it) }
                            db.playlistDao().touch(playlistId, System.currentTimeMillis())
                            if (playlistId == LikedPlaylist.ID) {
                                ids.forEach { id ->
                                    if (liked != null) liked.onRemoved(id) else db.trackDao().setFavorite(id, false)
                                }
                            }
                            val gone = ids.toSet()
                            orderState.value = orderState.value?.filter { it !in gone }
                        }
                    },
                    modifier = Modifier.hand(),
                ) { Text("删除", color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }, modifier = Modifier.hand()) { Text("取消") }
            },
        )
    }
    if (pickingPlaylist) {
        PlaylistPickerDialog(
            db = db,
            excludeId = playlistId,
            onDismiss = { pickingPlaylist = false },
            onPick = { target ->
                val picked = tracks.filter { it.id in selected }.map { it.toDomain(null) }
                pickingPlaylist = false
                exitSelection()
                scope.launch { notify(addToPlaylist(db, target, picked).notice()) }
            },
        )
    }

    coverCropSource?.let { src ->
        ImageCropDialog(
            source = src,
            aspect = 1f,
            maxSide = 512,
            outName = "pl_${playlistId}_${System.currentTimeMillis()}.jpg",
            title = "裁剪歌单封面",
            onConfirm = { out ->
                coverCropSource = null
                val prev = playlist?.coverUri
                scope.launch {
                    db.playlistDao().setCover(playlistId, out.toURI().toString())
                    db.playlistDao().touch(playlistId, System.currentTimeMillis())
                    prev?.let {
                        runCatching {
                            val f = File(java.net.URI(it))
                            if (f.absoluteFile.parentFile ==
                                DesktopSettings().artRoot().absoluteFile
                            ) {
                                f.delete()
                            }
                        }
                    }
                    playlistTick++
                }
            },
            onDismiss = { coverCropSource = null },
        )
    }

    if (renaming) {
        var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("重命名歌单", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    modifier = Modifier.hand(),
                    onClick = {
                        renaming = false
                        scope.launch {
                            db.playlistDao().rename(playlistId, name.trim(), System.currentTimeMillis())
                            playlistTick++
                        }
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }, modifier = Modifier.hand()) { Text("取消") }
            },
        )
    }
}

@Composable
private fun HeroCircleAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f), CircleShape)
            .hand()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}


private suspend fun savePlaylistOrder(db: RabBeatDatabase, playlistId: String, orderedVisibleIds: List<String>) {
    val existing = db.playlistDao().refs(playlistId).map { it.trackId }
    val existingSet = existing.toSet()
    val given = orderedVisibleIds.distinct().filter { it in existingSet }
    val givenSet = given.toSet()
    (given + existing.filter { it !in givenSet }).forEachIndexed { index, id ->
        db.playlistDao().setTrackPosition(playlistId, id, index)
    }
    db.playlistDao().touch(playlistId, System.currentTimeMillis())
}

data class PlaylistAddResult(val added: Int, val already: Int)

fun PlaylistAddResult.notice(): String = when {
    added > 0 && already == 0 -> if (added == 1) "已添加到歌单" else "已添加 $added 首到歌单"
    added == 0 -> if (already <= 1) "这首歌已经在歌单里了" else "这 $already 首已经在歌单里了"
    else -> "已添加 $added 首，$already 首已经在歌单里"
}

suspend fun addToPlaylist(db: RabBeatDatabase, playlistId: String, tracks: List<Track>): PlaylistAddResult {
    val requested = tracks.distinctBy { it.id }
    val have = db.playlistDao().refs(playlistId).map { it.trackId }.toSet()
    val fresh = requested.filter { it.id !in have }
    val already = requested.size - fresh.size
    if (fresh.isEmpty()) return PlaylistAddResult(0, already)
    val now = System.currentTimeMillis()
    db.playlistDao().shift(playlistId, fresh.size)
    db.playlistDao().upsertTracks(
        fresh.mapIndexed { i, t ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = t.id,
                position = i,
                addedAt = now,
            )
        },
    )
    if (playlistId == com.rabbeat.app.data.local.LikedPlaylist.ID) {
        fresh.chunked(400).forEach { chunk -> db.trackDao().markFavorites(chunk.map { it.id }) }
    }
    db.playlistDao().touch(playlistId, now)
    return PlaylistAddResult(fresh.size, already)
}
