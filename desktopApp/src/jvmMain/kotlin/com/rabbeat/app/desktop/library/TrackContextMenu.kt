package com.rabbeat.app.desktop.library
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.compositionLocalOf
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.desktop.ui.LocalBriefNotice
import com.rabbeat.app.desktop.ui.hand
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.PlaylistCover
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

val LocalLikedPlaylist = compositionLocalOf<LikedPlaylist?> { null }

/**
 * Right-click context menu that follows the app theme — foundation's
 * ContextMenuArea renders a hardcoded light menu, and Material3 DropdownMenu
 * plays a translucent fade-out flash on dismiss. A plain Popup removes
 * immediately on dismiss and stays anchored cleanly to the click position.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ThemedContextMenuArea(
    items: () -> List<ContextMenuItem>,
    content: @Composable () -> Unit,
) {
    var menuPos by remember { mutableStateOf<Offset?>(null) }
    val palette = LocalRabBeatPalette.current
    Box(
        Modifier.onPointerEvent(PointerEventType.Press) { e ->
            if (e.button == PointerButton.Secondary) {
                menuPos = e.changes.first().position
            }
        },
    ) {
        content()
        val pos = menuPos
        if (pos != null) {
            val positioner = remember(pos) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val margin = 8
                        val rawX = anchorBounds.left + pos.x.roundToInt()
                        val rawY = anchorBounds.top + pos.y.roundToInt()
                        val x = if (rawX + popupContentSize.width + margin > windowSize.width) {
                            (rawX - popupContentSize.width).coerceAtLeast(margin)
                        } else {
                            rawX.coerceAtLeast(margin)
                        }
                        val y = if (rawY + popupContentSize.height + margin > windowSize.height) {
                            (rawY - popupContentSize.height).coerceAtLeast(margin)
                        } else {
                            rawY.coerceAtLeast(margin)
                        }
                        return IntOffset(x, y)
                    }
                }
            }
            Popup(
                popupPositionProvider = positioner,
                onDismissRequest = { menuPos = null },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    color = palette.elevated,
                    contentColor = palette.label,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.separator),
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        Modifier
                            .width(IntrinsicSize.Max)
                            .widthIn(min = 160.dp, max = 240.dp)
                            .padding(vertical = 5.dp, horizontal = 5.dp),
                    ) {
                        items().forEach { item ->
                            val hover = remember { MutableInteractionSource() }
                            val hovered by hover.collectIsHoveredAsState()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (hovered) palette.label.copy(alpha = 0.08f) else Color.Transparent,
                                    )
                                    .hoverable(hover)
                                    .hand()
                                    .clickable {
                                        menuPos = null
                                        item.onClick()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps a composable with the desktop right-click context menu for a track:
 * play next / enqueue / favorite / hide / add to playlist (+ caller extras).
 */
@Composable
fun TrackContextMenu(
    track: TrackEntity,
    db: RabBeatDatabase,
    player: PlaybackController,
    inPlaylistId: String? = null,
    extraMenu: List<ContextMenuItem> = emptyList(),
    onHide: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var pickPlaylist by remember { mutableStateOf(false) }
    val notify = LocalBriefNotice.current
    val scope = rememberCoroutineScope()
    val domain = remember(track) { track.toDomain(null) }
    val liked = LocalLikedPlaylist.current

    val items = buildList {
        add(ContextMenuItem("下一首播放") { player.playNext(domain) })
        add(ContextMenuItem("添加到队列") { player.enqueue(domain) })
        add(ContextMenuItem(if (track.isFavorite) "取消喜欢" else "喜欢") {
            scope.launch { if (liked != null) liked.toggle(track.id) else db.trackDao().toggleFavorite(track.id) }
        })
        if (inPlaylistId != null) {
            add(ContextMenuItem("从歌单移除") {
                scope.launch {
                    db.playlistDao().removeTrack(inPlaylistId, track.id)
                    db.playlistDao().touch(inPlaylistId, System.currentTimeMillis())
                    if (inPlaylistId == LikedPlaylist.ID) {
                        if (liked != null) liked.onRemoved(track.id) else db.trackDao().setFavorite(track.id, false)
                    }
                }
            })
        }
        add(ContextMenuItem("添加到歌单…") { pickPlaylist = true })
        add(ContextMenuItem("匹配在线歌词") {
            scope.launch {
                val songId = NeteaseClient.songIdOf(track.id, track.sourceId, track.remoteId)
                val lrc = NeteaseClient.fetchLrc(track.title, track.artist, songId) ?: run {
                    DesktopLog.w("Lyric", "no lyric for '${track.title}' id=${track.id}")
                    return@launch
                }
                val saved = LyricLoader.save(domain, lrc)
                if (saved == null) DesktopLog.w("Lyric", "save failed for '${track.title}' id=${track.id}")
                else DesktopLog.i("Lyric", "saved lyric for '${track.title}' -> ${saved.name}")
            }
        })
        addAll(extraMenu)
        if (onHide != null) add(ContextMenuItem("隐藏") { onHide() })
    }

    ThemedContextMenuArea(items = { items }) { content() }

    if (pickPlaylist) {
        PlaylistPickerDialog(
            db = db,
            onDismiss = { pickPlaylist = false },
            onPick = { playlistId ->
                pickPlaylist = false
                scope.launch { notify(addToPlaylist(db, playlistId, listOf(domain)).notice()) }
            },
        )
    }
}

@Composable
internal fun PlaylistPickerDialog(
    db: RabBeatDatabase,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    excludeId: String? = null,
) {
    val playlists by db.playlistDao().observeAll().collectAsState(initial = emptyList())
    val counts by db.playlistDao().observeVisibleCounts().collectAsState(initial = emptyList())
    val artworks by db.playlistDao().observeLatestArtwork().collectAsState(initial = emptyList())
    val countMap = remember(counts) { counts.associate { it.playlistId to it.trackCount } }
    val artMap = remember(artworks) { artworks.associate { it.playlistId to it.artworkUri } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单", fontWeight = FontWeight.SemiBold) },
        text = {
            if (playlists.isEmpty()) {
                EmptyState(message = "还没有歌单，先在左侧建一个")
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(playlists.filter { it.id != excludeId }, key = { it.id }) { playlist ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val hovered by interactionSource.collectIsHoveredAsState()
                        val count = countMap[playlist.id] ?: 0
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                    else Color.Transparent,
                                )
                                .hoverable(interactionSource)
                                .hand()
                                .clickable { onPick(playlist.id) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaylistCover(
                                UserPlaylist(
                                    id = playlist.id,
                                    name = playlist.name,
                                    trackCount = count,
                                    updatedAt = playlist.updatedAt,
                                    isSystem = playlist.isSystem,
                                    coverUri = playlist.coverUri,
                                    autoCoverUri = artMap[playlist.id],
                                ),
                                size = 38.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "$count 首",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.hand()) { Text("取消") }
        },
    )
}
