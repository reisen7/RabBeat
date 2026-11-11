package com.rabbeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackOrigin
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

@Composable
fun SourceBadge(name: String, type: SourceType, modifier: Modifier = Modifier) {
    val color = when (type) {
        SourceType.LOCAL -> Color(0xFFAF52DE)
        SourceType.SUBSONIC -> Color(0xFF007AFF)
        SourceType.WEBDAV -> Color(0xFFFF9500)
        SourceType.SMB -> Color(0xFFFF2D55)
        SourceType.NETEASE -> Color(0xFFE60026)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = LocalRabBeatPalette.current.secondaryLabel, maxLines = 1)
    }
}

@Composable
fun OfflineBadge(track: Track) {
    val local = track.origin != TrackOrigin.REMOTE || track.downloadState == DownloadState.DOWNLOADED
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (local) Icons.Filled.Download else Icons.Filled.Cloud,
            contentDescription = stringResource(if (local) R.string.origin_cached else R.string.origin_remote),
            modifier = Modifier.size(14.dp),
            tint = LocalRabBeatPalette.current.secondaryLabel,
        )
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onFavorite: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    playing: Boolean = false,
) {
    var menu by remember(track.id) { mutableStateOf(false) }
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Artwork(track.artworkUri, track.title, Modifier.size(48.dp), 6.dp)
                if (playing) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerBars(playing = true, tint = Color.White, height = 14.dp, barWidth = 2.5.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (playing) accent.color else palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (track.isFavorite) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = accent.color,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    stringResource(R.string.track_meta, track.artist, track.album),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.MoreHoriz,
                        contentDescription = stringResource(R.string.cd_more),
                        tint = palette.secondaryLabel,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_play_next)) }, onClick = { menu = false; onPlayNext() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_enqueue)) }, onClick = { menu = false; onEnqueue() })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (track.isFavorite) R.string.menu_unfavorite else R.string.menu_favorite)) },
                        onClick = { menu = false; onFavorite() },
                    )
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_add_playlist)) }, onClick = { menu = false; onAddToPlaylist() })
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_remove_playlist)) }, onClick = { menu = false; onRemove() })
                    }
                    if (track.origin == TrackOrigin.REMOTE && track.downloadState != DownloadState.DOWNLOADED &&
                        track.sourceType != SourceType.NETEASE
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_download)) }, onClick = { menu = false; onDownload() })
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 80.dp)
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}

@Composable
fun TrackRowPlaceholder() {
    val palette = LocalRabBeatPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .background(palette.fill),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .width(160.dp)
                        .height(15.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(palette.fill),
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(110.dp)
                        .height(12.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                        .background(palette.fill),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 80.dp)
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}
