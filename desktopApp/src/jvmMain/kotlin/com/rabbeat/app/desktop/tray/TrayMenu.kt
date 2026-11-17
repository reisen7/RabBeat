package com.rabbeat.app.desktop.tray

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.rabbeat.app.desktop.ui.DesktopDarkPalette
import com.rabbeat.app.desktop.ui.hand
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.ui.theme.RabBeatTheme
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private val MenuWidth = 220.dp
private val MenuHeight = 280.dp
private val MenuMargin = 8.0

/**
 * Converts [anchor] (AWT screen pixels, from the tray click) to a window
 * position in dp. The menu opens above the anchor (tray is usually on a bottom
 * taskbar); if there is no room it flips below the anchor, then clamps to the
 * primary screen bounds.
 */
private fun trayMenuPosition(anchor: Point, width: Dp, height: Dp): WindowPosition {
    val scale = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
    }.getOrDefault(1.0)
    val screen = runCatching { Toolkit.getDefaultToolkit().screenSize }
        .getOrNull() ?: java.awt.Dimension(1920, 1080)
    val screenW = screen.width / scale
    val screenH = screen.height / scale
    val maxX = (screenW - width.value - MenuMargin).coerceAtLeast(MenuMargin)
    val maxY = (screenH - height.value - MenuMargin).coerceAtLeast(MenuMargin)
    val anchorX = anchor.x / scale
    val anchorY = anchor.y / scale
    val x = (anchorX - width.value / 2).coerceIn(MenuMargin, maxX)
    val above = anchorY - height.value - MenuMargin
    val y = if (above >= MenuMargin) {
        above
    } else {
        (anchorY + MenuMargin).coerceIn(MenuMargin, maxY)
    }
    return WindowPosition.Absolute(x.dp, y.dp)
}

/**
 * Themed popup menu shown next to the tray icon. A transparent, undecorated
 * UTILITY window (no taskbar/alt-tab entry) that dismisses itself when it
 * loses focus or the user picks an item.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TrayMenuWindow(
    anchor: Point,
    playState: PlaybackUiState,
    themeMode: ThemeMode,
    accentTheme: AccentTheme,
    onDismiss: () -> Unit,
    onShow: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQuit: () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val state = rememberWindowState(
        position = trayMenuPosition(anchor, MenuWidth, MenuHeight),
        width = MenuWidth,
        height = MenuHeight,
    )
    SwingWindow(
        onCloseRequest = { currentOnDismiss() },
        state = state,
        title = "RabBeat",
        decoration = WindowDecoration.Undecorated(resizerThickness = 0.dp),
        transparent = true,
        resizable = false,
        focusable = true,
        alwaysOnTop = true,
        onPreviewKeyEvent = { e ->
            if (e.type == KeyEventType.KeyUp && e.key == Key.Escape) {
                currentOnDismiss()
                true
            } else {
                false
            }
        },
        // UTILITY keeps the popup out of the taskbar and alt-tab; it must be
        // set before the window becomes displayable, so it lives in `init`.
        init = { w -> runCatching { w.type = java.awt.Window.Type.UTILITY } },
    ) {
        LaunchedEffect(Unit) {
            window.toFront()
            window.requestFocus()
        }
        DisposableEffect(Unit) {
            val listener = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent?) = currentOnDismiss()
                override fun windowDeactivated(e: WindowEvent?) = currentOnDismiss()
            }
            window.addWindowListener(listener)
            window.addWindowFocusListener(listener)
            onDispose {
                window.removeWindowListener(listener)
                window.removeWindowFocusListener(listener)
            }
        }
        RabBeatTheme(themeMode = themeMode, accentTheme = accentTheme, darkPalette = DesktopDarkPalette) {
            TrayMenuCard(
                playState = playState,
                onDismiss = { currentOnDismiss() },
                onShow = onShow,
                onToggle = onToggle,
                onPrev = onPrev,
                onNext = onNext,
                onQuit = onQuit,
            )
        }
    }
}

@Composable
private fun TrayMenuCard(
    playState: PlaybackUiState,
    onDismiss: () -> Unit,
    onShow: () -> Unit,
    onToggle: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQuit: () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val track = playState.currentTrack
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = palette.elevated,
        border = BorderStroke(1.dp, palette.separator),
    ) {
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            // Now-playing header
            Row(
                Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track?.artworkUri != null) {
                    Artwork(
                        artworkUri = track.artworkUri,
                        title = track.title,
                        modifier = Modifier.size(36.dp),
                        cornerRadius = 8.dp,
                    )
                } else {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = palette.secondaryLabel,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track?.title ?: "RabBeat",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artist ?: "未在播放",
                        fontSize = 11.sp,
                        color = palette.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            MenuDivider()

            TrayMenuItem("显示主面板", Icons.Default.OpenInFull) { onShow(); onDismiss() }
            TrayMenuItem(
                if (playState.isPlaying) "暂停" else "播放",
                if (playState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            ) { onToggle(); onDismiss() }
            TrayMenuItem("上一首", Icons.Default.SkipPrevious) { onPrev(); onDismiss() }
            TrayMenuItem("下一首", Icons.Default.SkipNext) { onNext(); onDismiss() }

            MenuDivider()

            TrayMenuItem(
                "退出",
                Icons.Default.PowerSettingsNew,
                tint = MaterialTheme.colorScheme.error,
            ) { onQuit(); onDismiss() }
        }
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(1.dp)
            .background(LocalRabBeatPalette.current.separator),
    )
}

@Composable
private fun TrayMenuItem(
    label: String,
    icon: ImageVector,
    tint: Color = LocalRabBeatPalette.current.label,
    onClick: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) accent.color.copy(alpha = 0.12f) else Color.Transparent)
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
