package com.rabbeat.app.desktop.ui
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Full now-playing surface — covers everything except the bottom player bar. */
@Composable
fun NowPlayingPage(
    db: RabBeatDatabase,
    player: PlaybackController,
    state: PlaybackUiState,
    onCollapse: () -> Unit,
) {
    val track = state.currentTrack
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val scope = rememberCoroutineScope()
    var lyricReload by remember { mutableStateOf(0) }
    val lyrics = remember(track?.id, lyricReload) {
        track?.let { LyricLoader.loadFor(it) } ?: emptyList()
    }
    val offsetMs = produceState(0L, track?.id) {
        if (track != null) value = db.trackDao().get(track.id)?.lyricOffsetMs ?: 0L
    }.value
    val lyricIndex = LyricLoader.currentIndex(lyrics, state.positionMs + offsetMs)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (track?.artworkUri != null) {
            Box(Modifier.fillMaxSize().clip(RectangleShape)) {
                Artwork(
                    track.artworkUri,
                    track.title,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.blur(80.dp),
                    0.dp,
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    if (palette.isDark) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.40f),
                ),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(accent.color.copy(alpha = 0.45f), palette.background),
                    ),
                ),
            )
        }

        IconButton(
            onClick = onCollapse,
            modifier = Modifier.padding(14.dp).size(40.dp).hand(),
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "收起",
                tint = palette.label,
                modifier = Modifier.size(28.dp),
            )
        }

        val compact = maxWidth < 1100.dp
        val pageHeight = maxHeight
        Row(
            Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(0.45f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                VinylDisc(
                    track = track,
                    playing = state.isPlaying,
                    discSize = if (compact) 260.dp else 320.dp,
                    artSize = if (compact) 180.dp else 220.dp,
                )
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(0.55f).fillMaxHeight()) {
                Text(
                    track?.title ?: "未在播放",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.let { "${it.artist} · ${it.album}" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                if (lyrics.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (track != null) "暂无歌词" else "未在播放",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.secondaryLabel,
                            )
                            if (track != null) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            NeteaseClient.fetchLrc(track.title, track.artist)?.let { lrc ->
                                                runCatching {
                                                    val audio = java.io.File(java.net.URI(track.uri))
                                                    java.io.File(audio.parentFile, audio.nameWithoutExtension + ".lrc").writeText(lrc)
                                                    lyricReload++
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.hand(),
                                ) {
                                    Text("在线匹配歌词")
                                }
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(lyricIndex) {
                        if (lyricIndex >= 0) listState.animateScrollToItem(lyricIndex)
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                top = (pageHeight * 0.35f - 60.dp).coerceAtLeast(0.dp),
                                bottom = pageHeight * 0.45f,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.1f to Color.Black,
                                            0.9f to Color.Black,
                                            1f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        ) {
                            itemsIndexed(lyrics) { i, line ->
                                val distance = abs(i - lyricIndex)
                                val isCurrent = i == lyricIndex
                                Text(
                                    line.text,
                                    fontSize = if (isCurrent) 24.sp else 16.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) accent.color
                                    else palette.label.copy(alpha = (0.75f - distance * 0.09f).coerceAtLeast(0.3f)),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                        .hand()
                                        .clickable { player.seekTo((line.atMs - offsetMs).coerceAtLeast(0L)) }
                                        .padding(vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        TextButton(
                            onClick = {
                                if (track != null) scope.launch { db.trackDao().updateLyricOffset(track.id, offsetMs - 500) }
                            },
                            modifier = Modifier.hand(),
                        ) { Text("-0.5s") }
                        Text(
                            "偏移 ${"%.1f".format(offsetMs / 1000f)}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryLabel,
                        )
                        TextButton(
                            onClick = {
                                if (track != null) scope.launch { db.trackDao().updateLyricOffset(track.id, offsetMs + 500) }
                            },
                            modifier = Modifier.hand(),
                        ) { Text("+0.5s") }
                    }
                }
            }
        }
    }
}

@Composable
private fun VinylDisc(track: Track?, playing: Boolean, discSize: Dp, artSize: Dp) {
    val angle = remember(track?.id) { Animatable(0f) }
    LaunchedEffect(playing) {
        if (!playing) {
            angle.stop()
            return@LaunchedEffect
        }
        val part = angle.value % 360f
        angle.animateTo(
            angle.value + (360f - part),
            tween(((1f - part / 360f) * 24_000).toInt().coerceAtLeast(1), easing = LinearEasing),
        )
        angle.animateTo(
            angle.value + 360f,
            infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        )
    }
    Box(
        Modifier.size(discSize).graphicsLayer { rotationZ = angle.value },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(Color(0xFF141416))
            drawCircle(
                Color.White.copy(alpha = 0.1f),
                radius = radius - 0.5.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            var r = radius - 6.dp.toPx()
            val hole = artSize.toPx() / 2f
            while (r > hole) {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = r, style = Stroke(width = 0.5.dp.toPx()))
                r -= 13.dp.toPx()
            }
        }
        Artwork(
            artworkUri = track?.artworkUri,
            title = track?.title ?: "?",
            modifier = Modifier.size(artSize).clip(CircleShape),
            cornerRadius = artSize / 2,
        )
        Box(
            Modifier
                .size(artSize * 0.18f)
                .clip(CircleShape)
                .background(Color(0xFF141418))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        )
    }
}
