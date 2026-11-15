package com.rabbeat.app.desktop

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun PlaylistListView(
    db: RabBeatDatabase,
    onOpen: (String) -> Unit,
) {
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    val counts by db.playlistDao().observeVisibleCounts().collectAsState(initial = emptyList())
    val artworks by db.playlistDao().observeLatestArtwork().collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val importer = remember { NeteaseImporter(db) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("歌单", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            importResult?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { importing = true }) {
                Icon(Icons.Default.CloudDownload, contentDescription = "导入网易云歌单")
            }
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建歌单")
            }
        }
        if (playlists.isEmpty()) {
            EmptyState(message = "还没有歌单")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(playlist.id) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            artworkUri = playlist.coverUri
                                ?: artworks.firstOrNull { it.playlistId == playlist.id }?.artworkUri,
                            title = playlist.name,
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 10.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${counts.firstOrNull { it.playlistId == playlist.id }?.trackCount ?: 0} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("歌单名称") })
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
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
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } },
        )
    }

    if (importing) {
        var link by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (busy == null) importing = false },
            title = { Text("导入网易云歌单") },
            text = {
                Column {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        singleLine = true,
                        placeholder = { Text("歌单链接或 ID") },
                        enabled = busy == null,
                    )
                    busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = link.isNotBlank() && busy == null,
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
                TextButton(enabled = busy == null, onClick = { importing = false }) { Text("取消") }
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
    onBack: () -> Unit,
) {
    val refs by db.playlistDao().observeRefs(playlistId).collectAsState(initial = emptyList())
    val all by db.trackDao().observeAll().collectAsState(initial = emptyList())
    val playlist = producePlaylist(db, playlistId)
    val scope = rememberCoroutineScope()
    val byId = remember(all) { all.associateBy { it.id } }
    val tracks = remember(refs, byId) { refs.mapNotNull { byId[it.trackId] } }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text(playlist?.name ?: "歌单", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            var renaming by remember { mutableStateOf(false) }
            IconButton(onClick = {
                pickImage { file ->
                    scope.launch {
                        val dest = cropSquareToArt(file, playlistId)
                        if (dest != null) db.playlistDao().setCover(playlistId, dest.toURI().toString())
                    }
                }
            }) {
                Icon(Icons.Default.Image, contentDescription = "设置封面")
            }
            IconButton(onClick = { renaming = true }) {
                Icon(Icons.Default.Edit, contentDescription = "重命名")
            }
            if (renaming) {
                var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
                AlertDialog(
                    onDismissRequest = { renaming = false },
                    title = { Text("重命名歌单") },
                    text = {
                        OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                    },
                    confirmButton = {
                        TextButton(
                            enabled = name.isNotBlank(),
                            onClick = {
                                renaming = false
                                scope.launch {
                                    db.playlistDao().rename(playlistId, name.trim(), System.currentTimeMillis())
                                }
                            },
                        ) { Text("保存") }
                    },
                    dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } },
                )
            }
            IconButton(onClick = {
                scope.launch {
                    db.playlistDao().deleteTracks(playlistId)
                    db.playlistDao().delete(playlistId)
                    onBack()
                }
            }) { Icon(Icons.Default.Delete, contentDescription = "删除歌单") }
        }
        if (tracks.isEmpty()) {
            EmptyState(message = "歌单还是空的，右键歌曲可加入歌单")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { i, track ->
                    val moveMenu = buildList {
                        if (i > 0) add(androidx.compose.foundation.ContextMenuItem("上移") {
                            val other = tracks[i - 1]
                            scope.launch {
                                db.playlistDao().setTrackPosition(playlistId, track.id, i - 1)
                                db.playlistDao().setTrackPosition(playlistId, other.id, i)
                            }
                        })
                        if (i < tracks.lastIndex) add(androidx.compose.foundation.ContextMenuItem("下移") {
                            val other = tracks[i + 1]
                            scope.launch {
                                db.playlistDao().setTrackPosition(playlistId, track.id, i + 1)
                                db.playlistDao().setTrackPosition(playlistId, other.id, i)
                            }
                        })
                    }
                    TrackContextMenu(
                        track = track,
                        db = db,
                        player = player,
                        inPlaylistId = playlistId,
                        extraMenu = moveMenu,
                    ) {
                        TrackMenuRow(
                            track = track,
                            playing = track.id == currentTrackId,
                            db = db,
                            onPlay = { player.playQueue(tracks.map { it.toDomain(null) }, i) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun producePlaylist(db: RabBeatDatabase, id: String): PlaylistEntity? {
    var playlist by remember { mutableStateOf<PlaylistEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) {
        playlist = db.playlistDao().get(id)
    }
    return playlist
}

/** Row with artwork + title, used wherever a playable track line is needed. */
@Composable
fun TrackMenuRow(
    track: TrackEntity,
    playing: Boolean,
    db: RabBeatDatabase,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    extraMenu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    TrackRowSurface(playing = playing, onPlay = onPlay) {
        Artwork(
            artworkUri = track.artworkUri,
            title = track.title,
            modifier = Modifier.size(44.dp),
            cornerRadius = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun TrackRowSurface(
    playing: Boolean,
    onPlay: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

private fun pickImage(onPicked: (java.io.File) -> Unit) {
    javax.swing.SwingUtilities.invokeLater {
        val chooser = javax.swing.JFileChooser(javax.swing.filechooser.FileSystemView.getFileSystemView().homeDirectory).apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("图片", "jpg", "jpeg", "png", "webp")
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            onPicked(chooser.selectedFile)
        }
    }
}

/** Center-crop to a 512px square and persist under ~/.rabbeat/art as the playlist cover. */
private fun cropSquareToArt(src: java.io.File, playlistId: String): java.io.File? = runCatching {
    val img = javax.imageio.ImageIO.read(src) ?: return null
    val side = minOf(img.width, img.height)
    val cropped = img.getSubimage((img.width - side) / 2, (img.height - side) / 2, side, side)
    val scaled = java.awt.image.BufferedImage(512, 512, java.awt.image.BufferedImage.TYPE_INT_RGB)
    scaled.createGraphics().apply {
        setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        drawImage(cropped, 0, 0, 512, 512, null)
        dispose()
    }
    val out = java.io.File(LibraryScanner.dataDir(), "art/pl_$playlistId.jpg").apply { parentFile?.mkdirs() }
    javax.imageio.ImageIO.write(scaled, "jpg", out)
    out
}.getOrNull()

suspend fun addToPlaylist(db: RabBeatDatabase, playlistId: String, tracks: List<Track>) {
    val existing = db.playlistDao().trackCount(playlistId)
    db.playlistDao().upsertTracks(
        tracks.mapIndexed { i, t ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = t.id,
                position = existing + i,
                addedAt = System.currentTimeMillis(),
            )
        },
    )
    db.playlistDao().touch(playlistId, System.currentTimeMillis())
}
