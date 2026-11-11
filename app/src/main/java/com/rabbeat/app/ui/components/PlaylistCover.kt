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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rabbeat.app.domain.model.UserPlaylist
import com.rabbeat.app.ui.theme.LocalAccentPalette

@Composable
fun PlaylistCover(playlist: UserPlaylist, size: Dp, modifier: Modifier = Modifier) {
    val accent = LocalAccentPalette.current
    val density = LocalDensity.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.linearGradient(
                    if (playlist.isSystem) {
                        listOf(Color(0xFFFF5E8A), Color(0xFFD4237A))
                    } else {
                        listOf(accent.color.copy(alpha = 0.62f), accent.color.copy(alpha = 0.28f))
                    },
                ),
            ),
    ) {
        if (playlist.isSystem) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size * 0.35f),
            )
        } else {
            Text(
                playlist.name.take(1),
                style = MaterialTheme.typography.displaySmall,
                fontSize = with(density) { (size * 0.3f).toSp() },
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.Center),
            )
            Icon(
                Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(size * 0.08f)
                    .size(size * 0.14f),
            )
        }
    }
}
