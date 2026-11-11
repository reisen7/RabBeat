package com.rabbeat.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs

private val coverColors = listOf(
    Color(0xFF7C5CFF) to Color(0xFFB69CFF),
    Color(0xFFFF8A3D) to Color(0xFFFFC46B),
    Color(0xFF2F80ED) to Color(0xFF7CC7FF),
    Color(0xFFF43F5E) to Color(0xFFFF9AB0),
    Color(0xFF10B981) to Color(0xFF7EF0C4),
    Color(0xFFC084FC) to Color(0xFFFFB3E8),
)

fun coverFallback(key: String): Color = coverColors[abs(key.hashCode()) % coverColors.size].first

@Composable
fun Artwork(
    artworkUri: String?,
    title: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val context = LocalContext.current
    val resId = remember(artworkUri) {
        if (artworkUri.isNullOrBlank()) 0
        else context.resources.getIdentifier(artworkUri.substringAfterLast('/'), "drawable", context.packageName)
    }
    val pair = coverColors[abs(title.hashCode()) % coverColors.size]
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(pair.first, pair.second))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            resId != 0 -> Image(
                painter = painterResource(resId),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            !artworkUri.isNullOrBlank() && !artworkUri.startsWith("android.resource://") -> AsyncImage(
                model = artworkUri,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            else -> Text(
                text = title.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.92f),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
