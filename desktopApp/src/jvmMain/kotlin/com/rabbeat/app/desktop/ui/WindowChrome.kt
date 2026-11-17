package com.rabbeat.app.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import java.awt.Cursor
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window

val LocalWindowScope = compositionLocalOf<WindowScope?> { null }
val LocalToggleMaximize = compositionLocalOf<(() -> Unit)?> { null }

/** Transparent hit area that drags the window; double-click toggles maximize. */
@Composable
fun DragRegion(modifier: Modifier = Modifier) {
    val scope = LocalWindowScope.current
    val toggle = LocalToggleMaximize.current
    if (scope == null) {
        Box(modifier)
        return
    }
    val window = scope.window
    Box(
        modifier.pointerInput(window, toggle) {
            var lastTapAt = 0L
            var lastTapPos = Offset.Zero
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPos = down.position
                var dragged = false
                var originWindow: Point? = null
                var originPointer: Point? = null
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val maximized = window is Frame && window.extendedState and Frame.MAXIMIZED_BOTH != 0
                    if (!dragged && !maximized &&
                        (change.position - downPos).getDistance() > viewConfiguration.touchSlop
                    ) {
                        dragged = true
                        originWindow = Point(window.x, window.y)
                        originPointer = MouseInfo.getPointerInfo()?.location?.let { Point(it) }
                    }
                    if (dragged) {
                        change.consume()
                        val originW = originWindow ?: continue
                        val originP = originPointer ?: continue
                        val now = MouseInfo.getPointerInfo()?.location ?: continue
                        window.setLocation(originW.x + now.x - originP.x, originW.y + now.y - originP.y)
                    }
                }
                if (!dragged && toggle != null) {
                    val at = down.uptimeMillis
                    if (at - lastTapAt < 480L && (down.position - lastTapPos).getDistance() < 48f) {
                        lastTapAt = 0L
                        toggle()
                    } else {
                        lastTapAt = at
                        lastTapPos = down.position
                    }
                }
            }
        },
    )
}

/** Windows-style caption buttons: minimize, maximize/restore, close. */
@Composable
fun WindowControls(
    placement: WindowPlacement,
    onMinimize: () -> Unit,
    onToggleMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalRabBeatPalette.current
    val maximized = placement == WindowPlacement.Maximized
    Row(modifier) {
        ChromeButton(onClick = onMinimize) { tint ->
            Icon(Icons.Default.Remove, contentDescription = "最小化", tint = tint, modifier = Modifier.size(16.dp))
        }
        ChromeButton(onClick = onToggleMaximize) { tint ->
            Icon(
                if (maximized) Icons.Default.FilterNone else Icons.Default.CropSquare,
                contentDescription = if (maximized) "还原" else "最大化",
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        ChromeButton(
            onClick = onClose,
            hoverBackground = Color(0xFFE81123),
            hoverTint = Color.White,
        ) { tint ->
            Icon(Icons.Default.Close, contentDescription = "关闭", tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ChromeButton(
    hoverBackground: Color = Color.Unspecified,
    hoverTint: Color? = null,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val hoverBg = if (hoverBackground == Color.Unspecified) {
        palette.label.copy(alpha = 0.10f)
    } else {
        hoverBackground
    }
    Box(
        Modifier
            .size(46.dp, 32.dp)
            .background(if (hovered) hoverBg else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(if (hovered && hoverTint != null) hoverTint else palette.label)
    }
}

private enum class ResizeEdge(val dx: Int, val dy: Int, val cursor: Int) {
    N(0, -1, Cursor.N_RESIZE_CURSOR),
    S(0, 1, Cursor.S_RESIZE_CURSOR),
    W(-1, 0, Cursor.W_RESIZE_CURSOR),
    E(1, 0, Cursor.E_RESIZE_CURSOR),
    NW(-1, -1, Cursor.NW_RESIZE_CURSOR),
    NE(1, -1, Cursor.NE_RESIZE_CURSOR),
    SW(-1, 1, Cursor.SW_RESIZE_CURSOR),
    SE(1, 1, Cursor.SE_RESIZE_CURSOR),
}

/** Invisible resize handles on the window edges/corners for the undecorated frame. */
@Composable
fun BoxScope.WindowResizeHandles(window: Window, enabled: Boolean) {
    if (!enabled) return
    ResizeHandle(ResizeEdge.N, window, Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp).height(6.dp))
    ResizeHandle(ResizeEdge.S, window, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp).height(6.dp))
    ResizeHandle(ResizeEdge.W, window, Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(vertical = 12.dp).width(6.dp))
    ResizeHandle(ResizeEdge.E, window, Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 12.dp).width(6.dp))
    ResizeHandle(ResizeEdge.NW, window, Modifier.align(Alignment.TopStart).size(12.dp))
    ResizeHandle(ResizeEdge.NE, window, Modifier.align(Alignment.TopEnd).size(12.dp))
    ResizeHandle(ResizeEdge.SW, window, Modifier.align(Alignment.BottomStart).size(12.dp))
    ResizeHandle(ResizeEdge.SE, window, Modifier.align(Alignment.BottomEnd).size(12.dp))
}

@Composable
private fun ResizeHandle(edge: ResizeEdge, window: Window, modifier: Modifier) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge, window) {
                var startBounds = Rectangle()
                var startPoint = Point()
                detectDragGestures(
                    onDragStart = {
                        startBounds = window.bounds
                        MouseInfo.getPointerInfo()?.location?.let { startPoint = Point(it) }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pt = MouseInfo.getPointerInfo()?.location
                        if (pt != null) {
                            val minW = window.minimumSize?.width ?: 1000
                            val minH = window.minimumSize?.height ?: 640
                            var w = startBounds.width
                            var h = startBounds.height
                            var x = startBounds.x
                            var y = startBounds.y
                            if (edge.dx > 0) {
                                w = (startBounds.width + pt.x - startPoint.x).coerceAtLeast(minW)
                            } else if (edge.dx < 0) {
                                w = (startBounds.width - (pt.x - startPoint.x)).coerceAtLeast(minW)
                                x = startBounds.x + startBounds.width - w
                            }
                            if (edge.dy > 0) {
                                h = (startBounds.height + pt.y - startPoint.y).coerceAtLeast(minH)
                            } else if (edge.dy < 0) {
                                h = (startBounds.height - (pt.y - startPoint.y)).coerceAtLeast(minH)
                                y = startBounds.y + startBounds.height - h
                            }
                            window.setBounds(x, y, w, h)
                        }
                    },
                )
            },
    )
}
