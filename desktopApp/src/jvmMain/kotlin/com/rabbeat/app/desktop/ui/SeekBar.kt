package com.rabbeat.app.desktop.ui
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rabbeat.app.ui.theme.LocalAccentPalette
import kotlin.math.roundToInt

fun Modifier.hand(): Modifier = pointerHoverIcon(PointerIcon.Hand)

internal fun seekFractionForX(x: Float, widthPx: Float): Float =
    if (widthPx <= 0f) 0f else (x / widthPx).coerceIn(0f, 1f)

/**
 * Custom slider (no M3 thumb). [commitOnRelease] = only preview during a drag;
 * the caller commits inside [onSeekFinished] — keeps seek storms off the engine.
 */
@Composable
fun SeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    commitOnRelease: Boolean = false,
    enabled: Boolean = true,
    onSeekFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    var widthPx by remember { mutableStateOf(0f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val active = hovered || dragFraction != null
    val trackHeight by animateDpAsState(if (active) 6.dp else 4.dp)
    val shown = (dragFraction ?: fraction).coerceIn(0f, 1f)
    val accent = LocalAccentPalette.current
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val density = LocalDensity.current

    Box(
        modifier
            .height(20.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .hoverable(interactionSource, enabled)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek(seekFractionForX(offset.x, widthPx))
                    onSeekFinished?.invoke()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset -> dragFraction = seekFractionForX(offset.x, widthPx) },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = seekFractionForX(change.position.x, widthPx)
                        onSeek(dragFraction ?: 0f)
                    },
                    onDragEnd = {
                        dragFraction = null
                        onSeekFinished?.invoke()
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(trackColor),
        )
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(trackHeight)
                .clip(CircleShape)
                .background(accent.color),
        )
        if (active) {
            val thumb = 12.dp
            Box(
                Modifier
                    .offset {
                        val half = with(density) { (thumb / 2).toPx() }
                        IntOffset((widthPx * shown - half).roundToInt().coerceIn(0, (widthPx - half * 2).roundToInt().coerceAtLeast(0)), 0)
                    }
                    .size(thumb)
                    .clip(CircleShape)
                    .background(accent.color),
            )
        }
    }
}
