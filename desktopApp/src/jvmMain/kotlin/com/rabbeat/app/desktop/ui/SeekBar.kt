package com.rabbeat.app.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rabbeat.app.ui.theme.LocalAccentPalette
import kotlin.math.abs
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
    val dragging = dragFraction != null
    val active = hovered || dragging
    val trackHeight by animateDpAsState(if (active) 6.dp else 4.dp, tween(140))
    val thumbScale by animateFloatAsState(
        targetValue = when {
            dragging -> 1.15f
            hovered -> 1f
            else -> 0f
        },
        animationSpec = tween(140),
    )

    val target = (dragFraction ?: fraction).coerceIn(0f, 1f)
    val smooth = remember { Animatable(target) }
    LaunchedEffect(target, dragging) {
        if (dragging) {
            smooth.snapTo(target)
        } else {
            val delta = abs(target - smooth.value)
            when {
                delta < 0.0005f -> smooth.snapTo(target)
                // Normal playback tick (~200ms) → linear interpolation so the bar glides at 60fps;
                // larger jumps (click-seek / track switch) → quick ease-out.
                delta < 0.03f -> smooth.animateTo(target, tween(180, easing = LinearEasing))
                else -> smooth.animateTo(target, tween(160, easing = FastOutSlowInEasing))
            }
        }
    }
    val shown = smooth.value.coerceIn(0f, 1f)
    val accent = LocalAccentPalette.current
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val density = LocalDensity.current
    // pointerInput keeps its first lambda. Without this, a later song still seeks
    // with the duration captured when the bar was first shown.
    val seekCallback by rememberUpdatedState(onSeek)
    val finishedCallback by rememberUpdatedState(onSeekFinished)

    Box(
        modifier
            .height(20.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .hoverable(interactionSource, enabled)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val width = size.width.toFloat().takeIf { it > 0f } ?: widthPx
                    val first = seekFractionForX(down.position.x, width)
                    dragFraction = first
                    seekCallback(first)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        val f = seekFractionForX(change.position.x, size.width.toFloat().takeIf { it > 0f } ?: width)
                        dragFraction = f
                        seekCallback(f)
                    }
                    dragFraction = null
                    finishedCallback?.invoke()
                }
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
        if (thumbScale > 0.02f) {
            val thumb = 12.dp
            Box(
                Modifier
                    .offset {
                        val half = with(density) { (thumb / 2).toPx() }
                        IntOffset(
                            (widthPx * shown - half).roundToInt()
                                .coerceIn(0, (widthPx - half * 2).roundToInt().coerceAtLeast(0)),
                            0,
                        )
                    }
                    .size(thumb)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .clip(CircleShape)
                    .background(accent.color),
            )
        }
    }
}
