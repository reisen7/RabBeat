package com.rabbeat.app.desktop.ui
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.EqualizerBars
import com.rabbeat.app.ui.components.LocalCardOpacity
import com.rabbeat.app.ui.components.LocalHasBackgroundImage
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

/** Right-edge queue drawer — sits over the content area, above nothing else. */
@Composable
fun BoxScope.QueueDrawer(
    state: PlaybackUiState,
    player: PlaybackController,
    open: Boolean,
    onClose: () -> Unit,
) {
    if (open) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
    }
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight().width(400.dp),
    ) {
        val palette = LocalRabBeatPalette.current
        val hasBg = LocalHasBackgroundImage.current
        val bgAlpha = if (hasBg) LocalCardOpacity.current.coerceIn(0.65f, 0.96f) else 1f
        val drawerBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = bgAlpha)
        val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        Row(
            Modifier
                .fillMaxSize()
                .shadow(16.dp, shape)
                .clip(shape)
                .background(drawerBg),
        ) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(borderColor))
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 10.dp, bottom = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "播放队列",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${state.queue.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { player.clearUpcoming() }, modifier = Modifier.hand()) {
                        Text("清空")
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.separator.copy(alpha = 0.35f)))
                if (state.queue.isEmpty()) {
                    EmptyState(message = "播放队列为空", modifier = Modifier.weight(1f))
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(open) {
                        if (open && state.currentIndex in state.queue.indices) {
                            listState.animateScrollToItem(state.currentIndex)
                        }
                    }
                    Box(Modifier.weight(1f).padding(top = 4.dp)) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(state.queue, key = { i, t -> "${t.id}_$i" }) { i, t ->
                                QueueRow(i, t, state, player)
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(i: Int, track: Track, state: PlaybackUiState, player: PlaybackController) {
    val accent = LocalAccentPalette.current
    val current = i == state.currentIndex
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .hoverable(interactionSource)
            .hand()
            .clickable { player.playQueue(state.queue, i) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (current) {
                EqualizerBars(playing = state.isPlaying, height = 12.dp, barWidth = 2.5.dp)
            } else {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Artwork(track.artworkUri, track.title, Modifier.size(40.dp), 8.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (current) accent.color else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hovered) {
            IconButton(
                onClick = { player.removeFromQueue(i) },
                modifier = Modifier.size(28.dp).hand(),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            Text(
                formatMs(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
