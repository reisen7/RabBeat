package com.rabbeat.app.ui.components

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.R
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import kotlin.math.abs
import kotlin.math.tanh
import kotlinx.coroutines.launch

@Composable
fun MiniPlayerBar(
    state: PlaybackUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
) {
    val track = state.currentTrack
    val accent = LocalAccentPalette.current
    val palette = LocalRabBeatPalette.current
    val progress = if (state.durationMs <= 0L) 0f else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    val animated by animateFloatAsState(progress, label = "mini-progress")
    var dragX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrev by rememberUpdatedState(onPrev)
    val maxDragPx = with(LocalDensity.current) { 88.dp.toPx() }

    Column(
        modifier
            .fillMaxWidth()
            .shadow(
                8.dp,
                RoundedCornerShape(14.dp),
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(adaptiveSurface(palette.elevated, 0.92f))
            .graphicsLayer {
                translationX = dragX
                alpha = if (size.width > 0f) (1f - abs(dragX) / size.width).coerceIn(0.3f, 1f) else 1f
            }
            .clickable(onClick = onOpen)
            .pointerInput(Unit) {
                val exitPx = { size.width + 32.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = 0f
                    var change = awaitHorizontalTouchSlopOrCancellation(down.id) { ch, over ->
                        ch.consume()
                        total += over
                    }
                    if (change == null) return@awaitEachGesture
                    dragX = tanh(total / maxDragPx) * maxDragPx
                    while (true) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - change.previousPosition.x
                        change.consume()
                        total += dx
                        dragX = tanh(total / maxDragPx) * maxDragPx
                    }
                    val dir = when {
                        total < 0f -> -1f
                        total > 0f -> 1f
                        else -> 0f
                    }
                    if (dir < 0f) currentOnNext() else if (dir > 0f) currentOnPrev()
                    scope.launch {
                        if (dir != 0f) {
                            animate(dragX, dir * exitPx(), animationSpec = tween(170, easing = FastOutLinearInEasing)) { v, _ -> dragX = v }
                            animate(-dir * exitPx(), 0f, animationSpec = tween(210, easing = FastOutSlowInEasing)) { v, _ -> dragX = v }
                        } else {
                            animate(dragX, 0f, animationSpec = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)) { v, _ -> dragX = v }
                        }
                    }
                }
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track?.artworkUri, track?.title ?: "", Modifier.size(44.dp), 6.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track?.title ?: stringResource(R.string.mini_nothing),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.artist ?: stringResource(R.string.slogan),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle, enabled = track != null) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.cd_pause else R.string.cd_play),
                    tint = palette.label,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onNext, enabled = track != null) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.cd_next),
                    tint = palette.label,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(palette.label.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(accent.color),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
