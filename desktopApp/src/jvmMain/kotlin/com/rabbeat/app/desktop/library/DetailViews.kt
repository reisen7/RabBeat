package com.rabbeat.app.desktop.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.desktop.sys.formatMs
import com.rabbeat.app.desktop.ui.TrackTable
import com.rabbeat.app.desktop.ui.hand
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

@Composable
fun TrackListScreen(
    db: RabBeatDatabase,
    player: PlaybackController,
    title: String,
    tracks: List<TrackEntity>,
    currentTrackId: String?,
    isPlaying: Boolean,
    badge: String = "列表",
    subtitle: String? = null,
    coverUri: String? = null,
    isCircleCover: Boolean = false,
    fallbackIcon: ImageVector? = null,
    showAlbum: Boolean = true,
    showPlayCount: Boolean = false,
    onOpenAlbum: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    TrackTable(
        db = db,
        player = player,
        tracks = tracks,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        showAlbum = showAlbum,
        showPlayCount = showPlayCount,
        onOpenAlbum = onOpenAlbum,
        headerContent = {
            DetailHeroHeader(
                badge = badge,
                title = title,
                metaLine = buildMetaLine(subtitle, tracks),
                tracks = tracks,
                player = player,
                onBack = onBack,
                cover = {
                    if (fallbackIcon != null && coverUri.isNullOrBlank()) {
                        DetailIconCover(fallbackIcon)
                    } else if (isCircleCover) {
                        Artwork(
                            artworkUri = coverUri ?: tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                            title = title,
                            modifier = Modifier.size(168.dp).shadow(12.dp, CircleShape).clip(CircleShape),
                            cornerRadius = 84.dp,
                        )
                    } else {
                        Artwork(
                            artworkUri = coverUri ?: tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                            title = title,
                            modifier = Modifier.size(168.dp).shadow(12.dp, RoundedCornerShape(14.dp)),
                            cornerRadius = 14.dp,
                        )
                    }
                },
            )
        },
    )
}

@Composable
fun DetailIconCover(icon: ImageVector) {
    val palette = LocalRabBeatPalette.current
    Box(
        Modifier
            .size(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.coverFill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = palette.coverGlyph,
            modifier = Modifier.size(64.dp),
        )
    }
}

@Composable
fun DetailHeroHeader(
    badge: String,
    title: String,
    metaLine: String,
    tracks: List<TrackEntity>,
    player: PlaybackController,
    onBack: () -> Unit,
    cover: @Composable () -> Unit,
    showBackAndBadge: Boolean = true,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 28.dp, vertical = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(168.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cover()
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f).height(168.dp)) {
                    if (showBackAndBadge) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(palette.fill)
                                    .hand()
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(accent.soft)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent.color,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        title,
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        metaLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    player.playQueue(tracks.map { it.toDomain(null) }, 0)
                                }
                            },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent.color,
                                contentColor = accent.onColor,
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp).hand(),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("播放全部", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { tracks.forEach { player.enqueue(it.toDomain(null)) } },
                            enabled = tracks.isNotEmpty(),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp).hand(),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("全部加入队列")
                        }
                        extraActions()
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        )
    }
}

fun buildMetaLine(subtitle: String?, tracks: List<TrackEntity>): String {
    val totalMs = tracks.sumOf { it.durationMs.coerceAtLeast(0L) }
    val parts = buildList {
        if (!subtitle.isNullOrBlank()) add(subtitle)
        add("${tracks.size} 首")
        if (totalMs > 0L) add("总时长 ${formatMs(totalMs)}")
    }
    return parts.joinToString(" · ")
}
