package com.rabbeat.app.desktop
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*
import com.rabbeat.app.desktop.tray.*
import com.rabbeat.app.desktop.ui.*

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.request.Options
import com.rabbeat.app.data.local.MIGRATION_1_2
import com.rabbeat.app.data.local.MIGRATION_2_3
import com.rabbeat.app.data.local.MIGRATION_3_4
import com.rabbeat.app.data.local.MIGRATION_4_5
import com.rabbeat.app.data.local.MIGRATION_5_6
import com.rabbeat.app.data.local.MIGRATION_6_7
import com.rabbeat.app.data.local.MIGRATION_7_8
import com.rabbeat.app.data.local.MIGRATION_8_9
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.ui.theme.RabBeatTheme
import java.awt.Dimension
import java.awt.Point
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.geom.RoundRectangle2D
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

private fun openDatabase(): RabBeatDatabase {
    val dir = LibraryScanner.dataDir().apply { mkdirs() }
    return Room.databaseBuilder<RabBeatDatabase>(File(dir, "rabbeat.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
        )
        .fallbackToDestructiveMigration(true)
        .build()
}

private fun debugTheme(): ThemeMode? = System.getProperty("rabbeat.theme")
    ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }

private fun debugWindowSize(): Pair<Int, Int>? = System.getProperty("rabbeat.window")
    ?.split("x", "X")
    ?.let { p ->
        val w = p.getOrNull(0)?.toIntOrNull() ?: return@let null
        val h = p.getOrNull(1)?.toIntOrNull() ?: return@let null
        w to h
    }

fun main() {
    if (!SingleInstance.acquire()) exitProcess(0)
    SingletonImageLoader.setSafe { context ->
        coil3.ImageLoader.Builder(context)
            .components {
                add(
                    Mapper<String, File> { data, _ ->
                        if (!data.startsWith("file:")) null
                        else runCatching { File(java.net.URI(data)) }.getOrNull()
                    },
                )
            }
            .build()
    }
    application {
    val db = openDatabase()
    val appScope = remember { CoroutineScope(Dispatchers.Main) }
    val settings = remember { DesktopSettings() }
    val player = remember { DesktopPlayerController(db, appScope, settings) }
    val keys = remember { ShellKeys() }
    var themeTick by remember { mutableStateOf(0) }
    var appearanceTick by remember { mutableStateOf(0) }
    var windowVisible by remember { mutableStateOf(true) }
    var activateTick by remember { mutableStateOf(0) }
    SingleInstance.onActivate = {
        windowVisible = true
        activateTick++
    }
    val startTarget = remember { System.getProperty("rabbeat.start").orEmpty() }
    val playerOpen = remember { mutableStateOf(startTarget == "nowplaying") }
    val queueOpen = remember { mutableStateOf(startTarget == "queue") }
    val playState by player.state.collectAsState()
    val windowState = rememberWindowState(
        position = WindowPosition.Aligned(Alignment.Center),
        width = (debugWindowSize()?.first ?: settings.windowW).dp,
        height = (debugWindowSize()?.second ?: settings.windowH).dp,
    )

    val persistWindowSize: () -> Unit = {
        if (windowState.placement == WindowPlacement.Floating) {
            settings.windowW = windowState.size.width.value.toInt()
            settings.windowH = windowState.size.height.value.toInt()
        }
    }
    val closeRequest: () -> Unit = {
        persistWindowSize()
        if (settings.closeToTray) {
            windowVisible = false
        } else {
            player.shutdown()
            exitApplication()
        }
    }
    val quitApp: () -> Unit = {
        persistWindowSize()
        player.shutdown()
        exitApplication()
    }
    val themeMode = debugTheme() ?: remember(themeTick) { ThemeMode.valueOf(settings.themeMode) }
    val accent = remember(themeTick) { AccentTheme.valueOf(settings.accent) }

    // --- Tray ---
    var trayMenuVisible by remember { mutableStateOf(false) }
    var trayMenuAnchor by remember { mutableStateOf(Point(0, 0)) }
    val showMainWindow: () -> Unit = {
        windowVisible = true
        // Reuse the single-instance activation path so a minimized window is
        // restored and raised, not just made visible.
        activateTick++
    }
    val trayImage = remember { AppIcons.trayIcon() }

    AppTrayIcon(
        iconImage = trayImage,
        onShow = showMainWindow,
        onMenu = { pt ->
            trayMenuAnchor = pt
            trayMenuVisible = true
        },
    )

    if (trayMenuVisible) {
        TrayMenuWindow(
            anchor = trayMenuAnchor,
            playState = playState,
            themeMode = themeMode,
            accentTheme = accent,
            onDismiss = { trayMenuVisible = false },
            onShow = {
                showMainWindow()
                trayMenuVisible = false
            },
            onToggle = player::toggle,
            onPrev = player::previous,
            onNext = player::next,
            onQuit = quitApp,
        )
    }

    if (windowVisible) Window(
        onCloseRequest = closeRequest,
        undecorated = true,
        title = "RabBeat",
        state = windowState,
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyUp) return@Window false
            when {
                e.isCtrlPressed && e.key == Key.DirectionRight -> { player.next(); true }
                e.isCtrlPressed && e.key == Key.DirectionLeft -> { player.previous(); true }
                e.isCtrlPressed && e.key == Key.F -> { keys.searchFocus.requestFocus(); true }
                e.key == Key.Spacebar && (e.isCtrlPressed || !keys.searchFocused) -> { player.toggle(); true }
                e.key == Key.Escape && queueOpen.value -> { queueOpen.value = false; true }
                e.key == Key.Escape && playerOpen.value -> { playerOpen.value = false; true }
                else -> false
            }
        },
    ) {
        window.minimumSize = Dimension(1000, 640)
        // Windows 11 rounded corners via DWM; older systems fall back to an
        // AWT window shape (re-applied on resize, cleared while maximized).
        var dwmRounded by remember { mutableStateOf<Boolean?>(null) }
        SideEffect {
            WinNative.installBorderlessFrame(window)
        }
        // Compose rasterizes a window icon as one 192px image and lets Windows
        // scale it. That scale is what makes the taskbar copy soft. Apply the
        // pre-sized set after the window opens, so it replaces that single image.
        DisposableEffect(window) {
            val applyIcons = {
                window.iconImages = AppIcons.windowIcons()
            }
            val listener = object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    applyIcons()
                }
            }
            window.addWindowListener(listener)
            applyIcons()
            onDispose { window.removeWindowListener(listener) }
        }
        LaunchedEffect(Unit) {
            dwmRounded = WinNative.applyRoundCorners(window)
        }
        LaunchedEffect(dwmRounded, windowState.size, windowState.placement) {
            if (dwmRounded == false) {
                window.shape = if (windowState.placement == WindowPlacement.Maximized) {
                    null
                } else {
                    RoundRectangle2D.Float(
                        0f, 0f, window.width.toFloat(), window.height.toFloat(), 16f, 16f,
                    )
                }
            }
        }
        LaunchedEffect(activateTick) {
            if (activateTick > 0) {
                WinNative.toForeground(window)
                window.requestFocus()
            }
        }
        fun toggleMaximize() {
            if (windowState.placement == WindowPlacement.Maximized) {
                if (!WinNative.restore(window)) windowState.placement = WindowPlacement.Floating
            } else if (!WinNative.maximize(window)) {
                windowState.placement = WindowPlacement.Maximized
            }
        }
        RabBeatTheme(themeMode = themeMode, accentTheme = accent, darkPalette = DesktopDarkPalette) {
            val palette = LocalRabBeatPalette.current
            CompositionLocalProvider(
                LocalWindowScope provides this,
                LocalToggleMaximize provides ::toggleMaximize,
            ) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().border(1.dp, palette.separator)) {
                        DragRegion(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .align(Alignment.TopCenter),
                        )
                        AppShell(
                            db = db,
                            player = player,
                            scope = appScope,
                            settings = settings,
                            activeThemeMode = themeMode.name,
                            playerOpen = playerOpen,
                            queueOpen = queueOpen,
                            keys = keys,
                            appearanceTick = appearanceTick,
                            bgAspect = windowState.size.width.value
                                .let { w -> windowState.size.height.value.let { h -> if (w > 0 && h > 0) w / h else 16f / 9f } },
                            onThemeChanged = { themeTick++ },
                            onAppearanceChanged = { appearanceTick++ },
                        )
                        WindowControls(
                            placement = windowState.placement,
                            onMinimize = {
                                if (!WinNative.minimize(window)) windowState.isMinimized = true
                            },
                            onToggleMaximize = ::toggleMaximize,
                            onClose = closeRequest,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        WindowResizeHandles(
                            window = window,
                            enabled = windowState.placement == WindowPlacement.Floating,
                        )
                    }
                }
            }
        }
    }
    }
}
