package com.rabbeat.app.desktop
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*
import com.rabbeat.app.desktop.sys.*
import com.rabbeat.app.desktop.ui.*

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
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
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.sharedui.generated.resources.Res
import com.rabbeat.app.sharedui.generated.resources.ic_desktop_icon
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.ui.theme.RabBeatTheme
import java.awt.Dimension
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.compose.resources.painterResource

private fun openDatabase(): RabBeatDatabase {
    val dir = LibraryScanner.dataDir().apply { mkdirs() }
    return Room.databaseBuilder<RabBeatDatabase>(File(dir, "rabbeat.db").absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
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
    val startTarget = remember { System.getProperty("rabbeat.start").orEmpty() }
    val playerOpen = remember { mutableStateOf(startTarget == "nowplaying") }
    val queueOpen = remember { mutableStateOf(startTarget == "queue") }
    val playState by player.state.collectAsState()
    val windowState = rememberWindowState(
        width = (debugWindowSize()?.first ?: settings.windowW).dp,
        height = (debugWindowSize()?.second ?: settings.windowH).dp,
    )

    Tray(
        icon = painterResource(Res.drawable.ic_desktop_icon),
        tooltip = "RabBeat",
        onAction = { windowVisible = true },
        menu = {
            Item("显示 RabBeat", onClick = { windowVisible = true })
            Item(if (playState.isPlaying) "暂停" else "播放", onClick = { player.toggle() })
            Item("下一首", onClick = { player.next() })
            Item("上一首", onClick = { player.previous() })
            Separator()
            Item("退出", onClick = {
                settings.windowW = windowState.size.width.value.toInt()
                settings.windowH = windowState.size.height.value.toInt()
                player.shutdown()
                exitApplication()
            })
        },
    )

    if (windowVisible) Window(
        onCloseRequest = {
            settings.windowW = windowState.size.width.value.toInt()
            settings.windowH = windowState.size.height.value.toInt()
            if (settings.closeToTray) {
                windowVisible = false  // close → minimize to tray
            } else {
                player.shutdown()
                exitApplication()
            }
        },
        title = "RabBeat",
        icon = painterResource(Res.drawable.ic_desktop_icon),
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
        val themeMode = debugTheme() ?: remember(themeTick) { ThemeMode.valueOf(settings.themeMode) }
        val accent = remember(themeTick) { AccentTheme.valueOf(settings.accent) }
        RabBeatTheme(themeMode = themeMode, accentTheme = accent) {
            val isDark = LocalRabBeatPalette.current.isDark
            LaunchedEffect(window, isDark) { WindowsTitleBar.apply(window, isDark) }
            Surface(Modifier.fillMaxSize()) {
                key(appearanceTick) {
                    AppShell(
                        db = db,
                        player = player,
                        scope = appScope,
                        settings = settings,
                        activeThemeMode = themeMode.name,
                        playerOpen = playerOpen,
                        queueOpen = queueOpen,
                        keys = keys,
                        onThemeChanged = { themeTick++ },
                        onAppearanceChanged = { appearanceTick++ },
                    )
                }
            }
        }
    }
    }
}
