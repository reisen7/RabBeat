package com.rabbeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.ui.theme.LocalRabBeatPalette

@Composable
fun PlaylistCover(
    playlist: UserPlaylist,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 8.dp,
) {
    val palette = LocalRabBeatPalette.current
    val density = LocalDensity.current
    val cover = playlist.displayCoverUri
    if (cover != null) {
        Artwork(cover, playlist.name, modifier.size(size), 8.dp)
        return
    }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(palette.coverFill),
    ) {
        if (playlist.isSystem) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = palette.coverGlyph,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.38f),
            )
        } else {
            Text(
                playlist.name.take(1),
                style = MaterialTheme.typography.displaySmall,
                fontSize = with(density) { (size * 0.3f).toSp() },
                color = palette.coverGlyph,
                modifier = Modifier.align(Alignment.Center),
            )
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = palette.coverGlyph,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(size * 0.08f)
                    .size(size * 0.14f),
            )
        }
    }
}
