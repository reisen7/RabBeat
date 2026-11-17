package com.rabbeat.app.desktop.sys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.RoomRawQuery
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.clearScannedTracks
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.desktop.library.LibraryScanner
import com.rabbeat.app.desktop.net.NeteaseImporter
import com.rabbeat.app.sync.SyncQr
import com.rabbeat.app.desktop.player.DesktopPlayerController
import com.rabbeat.app.desktop.ui.ImageCropDialog
import com.rabbeat.app.desktop.ui.SeekBar
import com.rabbeat.app.desktop.ui.hand
import com.rabbeat.app.desktop.ui.translucentSurface
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.LocalHasBackgroundImage
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.ui.theme.palette
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun SettingsView(
    settings: DesktopSettings,
    activeThemeMode: String = settings.themeMode,
    scanner: LibraryScanner,
    db: RabBeatDatabase,
    player: DesktopPlayerController,
    bgAspect: Float = 16f / 9f,
    onThemeChange: () -> Unit,
    onAppearanceChange: () -> Unit,
    onOpenHidden: () -> Unit,
    scanText: String? = null,
    onImport: () -> Unit = {},
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pendingConfirm by remember { mutableStateOf<PendingConfirm?>(null) }
    var bgImage by remember { mutableStateOf(settings.bgImage) }
    var dimLevel by remember { mutableStateOf(settings.dimLevel) }
    var bgBlur by remember { mutableStateOf(settings.bgBlur) }
    var cardOpacity by remember { mutableStateOf(settings.cardOpacity) }
    val trackCount by db.trackDao().observeCount(
        TrackQuerySql.count(TrackQuery()),
    ).collectAsState(initial = 0)
    val storedBytes by db.trackDao().observeStoredBytes().collectAsState(initial = 0L)
    var updateDialog by remember { mutableStateOf<DesktopUpdate.Available?>(null) }
    var checking by remember { mutableStateOf(false) }
    val syncHost = remember { SyncHost(db, settings) }
    DisposableEffect(syncHost) { onDispose { syncHost.stop() } }
    val syncRunning by syncHost.running.collectAsState()
    val syncCode by syncHost.code.collectAsState()
    val syncAddresses by syncHost.addresses.collectAsState()
    val syncStatus by syncHost.status.collectAsState()
    var cropSource by remember { mutableStateOf<File?>(null) }

    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val scrollState = rememberScrollState()

    val accentItems = listOf(
        AccentTheme.ROSE to "玫瑰粉",
        AccentTheme.AMBER to "蜜橙",
        AccentTheme.MINT to "薄荷绿",
        AccentTheme.OCEAN to "湖蓝",
        AccentTheme.VIOLET to "薰衣紫",
    )
    val themeModes = listOf(
        Triple("LIGHT", "浅色", Icons.Default.LightMode),
        Triple("DARK", "深色", Icons.Default.DarkMode),
        Triple("SYSTEM", "跟随系统", Icons.Default.BrightnessAuto),
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 28.dp, vertical = 22.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.fill)
                            .hand()
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "设置",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(22.dp))
                SectionTitle("外观")
                SettingsCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("主题模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("切换浅色、深色或跟随系统", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
                        }
                        Row(
                            Modifier
                                .clip(CircleShape)
                                .background(palette.fill)
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            themeModes.forEach { (mode, label, icon) ->
                                val selected = activeThemeMode == mode
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) accent.color else Color.Transparent)
                                        .hand()
                                        .clickable {
                                            settings.themeMode = mode
                                            onThemeChange()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (selected) accent.onColor else palette.secondaryLabel,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) accent.onColor else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    CardDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("主题色", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("自定义高光与交互强调色", style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            accentItems.forEach { (theme, label) ->
                                val swatchColor = theme.palette(palette.isDark).color
                                val selected = settings.accent == theme.name
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .hand()
                                        .clickable {
                                            settings.accent = theme.name
                                            onThemeChange()
                                        }
                                        .padding(4.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .then(
                                                if (selected) Modifier.border(2.dp, swatchColor, CircleShape).padding(3.dp)
                                                else Modifier.padding(2.dp),
                                            )
                                            .clip(CircleShape)
                                            .background(swatchColor),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) swatchColor else palette.secondaryLabel,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("个性化背景")
                SettingsCard {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("背景图片", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (bgImage.isBlank()) "未设置自定义背景图" else File(bgImage).name,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (bgImage.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    settings.bgImage = ""; bgImage = ""
                                    onAppearanceChange()
                                },
                                modifier = Modifier.hand(),
                            ) { Text("清除") }
                            Spacer(Modifier.width(6.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    pickFile("选择图片", "jpg", "jpeg", "png", "webp")?.let { file ->
                                        cropSource = file
                                    }
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).hand(),
                        ) { Text("选择图片") }
                    }
                    if (bgImage.isNotBlank()) {
                        CardDivider()
                        SeekSettingRow(
                            label = "背景模糊",
                            fraction = (bgBlur / 40f).coerceIn(0f, 1f),
                            display = "${bgBlur.toInt()} dp",
                            onFractionChange = {
                                bgBlur = (it * 40f).coerceIn(0f, 40f)
                                settings.bgBlur = bgBlur
                                onAppearanceChange()
                            },
                        )
                        CardDivider()
                        SeekSettingRow(
                            label = "背景压暗",
                            fraction = (dimLevel / 0.8f).coerceIn(0f, 1f),
                            display = "${(dimLevel * 100).toInt()}%",
                            onFractionChange = {
                                dimLevel = (it * 0.8f).coerceIn(0f, 0.8f); settings.dimLevel = dimLevel
                                onAppearanceChange()
                            },
                        )
                        CardDivider()
                        SeekSettingRow(
                            label = "卡片不透明度",
                            fraction = cardOpacity.coerceIn(0f, 1f),
                            display = "${(cardOpacity * 100).toInt()}%",
                            onFractionChange = {
                                cardOpacity = it.coerceIn(0f, 1f); settings.cardOpacity = cardOpacity
                                onAppearanceChange()
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("系统")
                SettingsCard {
                    var closeToTray by remember { mutableStateOf(settings.closeToTray) }
                    ToggleSettingRow(
                        title = "关闭时隐藏到系统托盘",
                        subtitle = "开启后点右上角关闭会收进托盘继续播放，关闭则直接退出程序",
                        checked = closeToTray,
                        onCheckedChange = { closeToTray = it; settings.closeToTray = it },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("曲库与缓存")
                SettingsCard {
                    ActionSettingRow(
                        title = "导入音乐文件夹",
                        subtitle = scanText ?: "选择一个文件夹扫描本地歌曲",
                        trailingText = "导入",
                        onClick = onImport,
                    )
                    CardDivider()
                    var cacheTick by remember { mutableStateOf(0) }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("缓存位置", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                remember(cacheTick, settings.cacheDir) { settings.cacheRoot().absolutePath },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (settings.cacheDir.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val oldArt = settings.artRoot()
                                        val oldLyrics = settings.lyricRoot()
                                        val dest = File(LibraryScanner.dataDir(), "cache")
                                        withContext(Dispatchers.IO) {
                                            moveCacheContents(settings.cacheRoot(), dest)
                                        }
                                        settings.cacheDir = ""
                                        val rewrite = relocateCacheExtras(db, settings, oldArt, oldLyrics)
                                        player.remapArtwork(rewrite)
                                        onAppearanceChange()
                                        status = "缓存、封面和歌词已搬回默认位置"
                                        cacheTick++
                                    }
                                },
                                modifier = Modifier.hand(),
                            ) { Text("恢复默认") }
                            Spacer(Modifier.width(6.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val picked = pickDirectory("选择缓存位置") ?: return@launch
                                    val newRoot = File(picked, "RabBeatCache")
                                    if (newRoot.absoluteFile == settings.cacheRoot().absoluteFile) return@launch
                                    val oldArt = settings.artRoot()
                                    val oldLyrics = settings.lyricRoot()
                                    withContext(Dispatchers.IO) {
                                        moveCacheContents(settings.cacheRoot(), newRoot)
                                    }
                                    settings.cacheDir = newRoot.absolutePath
                                    val rewrite = relocateCacheExtras(db, settings, oldArt, oldLyrics)
                                    player.remapArtwork(rewrite)
                                    onAppearanceChange()
                                    status = "缓存、封面和歌词已搬到新位置"
                                    cacheTick++
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).hand(),
                        ) { Text("更改") }
                    }
                    CardDivider()
                    var musicTick by remember { mutableStateOf(0) }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("转换歌曲目录", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                remember(musicTick, settings.convertDir) { settings.convertRoot().absolutePath },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "扫描到 VIP 加密歌曲（ncm/qmc）时自动解密成普通音频存到这里",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                        if (settings.convertDir.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val from = settings.convertRoot()
                                        val to = File(LibraryScanner.dataDir(), "music")
                                        val outcome = withContext(Dispatchers.IO) {
                                            relocateConvertLibrary(db, from, to)
                                        }
                                        status = applyConvertMove(settings, player, from, to, outcome, useDefault = true)
                                        musicTick++
                                    }
                                },
                                modifier = Modifier.hand(),
                            ) { Text("恢复默认") }
                            Spacer(Modifier.width(6.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val picked = pickDirectory("选择转换歌曲目录") ?: return@launch
                                    val from = settings.convertRoot()
                                    val to = picked.absoluteFile
                                    val outcome = withContext(Dispatchers.IO) {
                                        relocateConvertLibrary(db, from, to)
                                    }
                                    status = applyConvertMove(settings, player, from, to, outcome, useDefault = false)
                                    musicTick++
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).hand(),
                        ) { Text("更改") }
                    }
                    CardDivider()
                    ActionSettingRow(
                        title = "已隐藏的歌曲",
                        subtitle = "查看或恢复之前隐藏的曲目",
                        trailingText = null,
                        showChevron = true,
                        onClick = onOpenHidden,
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "清空已扫描歌曲",
                        subtitle = "清除扫描进曲库的记录，不删除文件",
                        trailingText = "清空",
                        onClick = {
                            pendingConfirm = PendingConfirm(
                                title = "清空已扫描歌曲",
                                text = "将清除通过「导入音乐文件夹」扫进曲库的全部记录。硬盘上的歌曲文件会保留，网易云导入的歌曲不受影响。确定继续吗？",
                            ) {
                                val n = db.clearScannedTracks()
                                status = "已清除 $n 首扫描记录"
                            }
                        },
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "清理失效歌曲",
                        subtitle = "移除本地文件已不存在的曲库记录",
                        trailingText = "清理",
                        onClick = {
                            pendingConfirm = PendingConfirm(
                                title = "清理失效歌曲",
                                text = "将移除本地文件已不存在的曲库记录，确定继续吗？",
                            ) {
                                val n = scanner.prune()
                                status = "已清理 $n 首失效歌曲"
                            }
                        },
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "清理在线缓存",
                        subtitle = "只删除已下载的音频缓存，曲库里的歌还在",
                        trailingText = "清理",
                        onClick = {
                            pendingConfirm = PendingConfirm(
                                title = "清理在线缓存",
                                text = "将删除已下载的在线曲目和解密缓存。封面和歌词会保留。",
                            ) {
                                val dir = settings.cacheRoot()
                                val files = dir.listFiles()?.filter { it.name != "art" && it.name != "lyrics" }.orEmpty()
                                val freed = files.sumOf { it.length() }
                                files.forEach { it.deleteRecursively() }
                                status = "已释放 ${fmtBytes(freed)}"
                            }
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("和手机同步")
                SettingsCard {
                    if (!syncRunning) {
                        ActionSettingRow(
                            title = "和手机同步歌单",
                            subtitle = "同一 Wi-Fi。歌单里缺的歌会带上音频、封面和歌词",
                            trailingText = "开始",
                            onClick = { syncHost.start() },
                        )
                        if (syncStatus != "未开始" && syncStatus != "已停止") {
                            Text(
                                syncStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                            )
                        }
                    } else {
                        val qr = remember(syncCode, syncAddresses) {
                            if (syncCode.length == 6 && syncAddresses.isNotEmpty()) {
                                runCatching { syncQrBitmap(SyncQr.encode(syncAddresses, syncCode)) }.getOrNull()
                            } else {
                                null
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                            if (qr != null) {
                                Box(
                                    Modifier
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                ) {
                                    Image(
                                        bitmap = qr,
                                        contentDescription = "同步二维码",
                                        modifier = Modifier.size(180.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(syncStatus, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "手机设置里选「和电脑同步」，扫这个二维码。同步时留在这个页面。连不上时，在 Windows 防火墙里允许本程序。",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { syncHost.stop() },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp).hand(),
                            ) { Text("停止") }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("数据与更新")
                SettingsCard {
                    ActionSettingRow(
                        title = "导出数据库备份",
                        subtitle = "复制曲库数据库到指定位置",
                        trailingText = "导出",
                        onClick = {
                            scope.launch {
                                pickSave("rabbeat-backup.db")?.let { dest ->
                                    runCatching {
                                        File(LibraryScanner.dataDir(), "rabbeat.db").copyTo(dest, overwrite = true)
                                        status = "备份已导出到 ${dest.name}"
                                    }.onFailure { status = "导出失败：${it.message}" }
                                }
                            }
                        },
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "恢复数据库备份",
                        subtitle = "选择备份文件覆盖当前库（需重启生效）",
                        trailingText = "选择文件",
                        onClick = {
                            scope.launch {
                                pickFile("数据库", "db")?.let { src ->
                                    runCatching {
                                        src.copyTo(File(LibraryScanner.dataDir(), "rabbeat.db"), overwrite = true)
                                        status = "已恢复，重启应用生效"
                                    }.onFailure { status = "恢复失败：${it.message}" }
                                }
                            }
                        },
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "导出运行日志",
                        subtitle = "合并最近 7 天运行日志",
                        trailingText = "导出",
                        onClick = {
                            scope.launch {
                                pickSave("rabbeat-logs-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.log")?.let { dest ->
                                    DesktopLog.export(dest)
                                    status = "日志已导出到 ${dest.name}"
                                }
                            }
                        },
                    )
                    CardDivider()
                    Row(
                        Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("曲库统计", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(
                            "$trackCount 首 · ${fmtBytes(storedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.secondaryLabel,
                        )
                    }
                    CardDivider()
                    ActionSettingRow(
                        title = "版本与更新",
                        subtitle = "当前版本 ${DesktopUpdate.CURRENT}",
                        trailingText = if (checking) "检查中…" else "检查更新",
                        onClick = {
                            if (checking) return@ActionSettingRow
                            scope.launch {
                                checking = true
                                when (val result = DesktopUpdate.check()) {
                                    is DesktopUpdate.Check.Update -> updateDialog = result.release
                                    DesktopUpdate.Check.Latest -> status = "已是最新版本"
                                    DesktopUpdate.Check.Failed -> status = "检查更新失败，请稍后再试"
                                }
                                checking = false
                            }
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("快捷键速查")
                SettingsCard {
                    val shortcuts = listOf(
                        "Space" to "播放 / 暂停",
                        "Ctrl + ← / →" to "上一首 / 下一首",
                        "Ctrl + F" to "聚焦顶部搜索框",
                        "Esc" to "收起播放详情页或队列抽屉",
                    )
                    shortcuts.forEachIndexed { idx, (keyLabel, desc) ->
                        if (idx > 0) CardDivider()
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.fill)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    keyLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
        )
    }

    cropSource?.let { src ->
        ImageCropDialog(
            source = src,
            aspect = bgAspect,
            maxSide = 2560,
            outName = "bg_${System.currentTimeMillis()}.jpg",
            title = "裁剪背景图片",
            onConfirm = { out ->
                cropSource = null
                val prev = bgImage
                settings.bgImage = out.absolutePath; bgImage = out.absolutePath
                if (prev.isNotBlank()) {
                    runCatching {
                        val pf = File(prev)
                        val artDir = settings.artRoot().absoluteFile
                        if (pf.absoluteFile.parentFile == artDir && pf.absoluteFile != out.absoluteFile) {
                            pf.delete()
                        }
                    }
                }
                onAppearanceChange()
            },
            onDismiss = { cropSource = null },
        )
    }

    status?.let { message ->
        AlertDialog(
            onDismissRequest = { status = null },
            title = { Text("提示", fontWeight = FontWeight.SemiBold) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    modifier = Modifier.hand(),
                    onClick = { status = null },
                ) { Text("知道了") }
            },
        )
    }

    pendingConfirm?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(c.title, fontWeight = FontWeight.SemiBold) },
            text = { Text(c.text, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    modifier = Modifier.hand(),
                    onClick = {
                        pendingConfirm = null
                        scope.launch { c.action() }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }, modifier = Modifier.hand()) { Text("取消") }
            },
        )
    }

    updateDialog?.let { rel ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            title = { Text("发现新版本 ${rel.tag}", fontWeight = FontWeight.SemiBold) },
            text = {
                ReleaseNotes(
                    rel.notes,
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.color,
                        contentColor = accent.onColor,
                    ),
                    modifier = Modifier.hand(),
                    onClick = {
                        DesktopUpdate.browse(rel.assetUrl ?: rel.pageUrl)
                        updateDialog = null
                    },
                ) { Text(if (rel.assetUrl != null) "下载" else "查看") }
            },
            dismissButton = {
                TextButton(onClick = { updateDialog = null }, modifier = Modifier.hand()) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = LocalRabBeatPalette.current.secondaryLabel,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val palette = LocalRabBeatPalette.current
    val hasBg = LocalHasBackgroundImage.current
    val bg = if (hasBg) translucentSurface() else if (palette.isDark) palette.card else palette.elevated.copy(alpha = 0.65f)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

@Composable
private fun ActionSettingRow(
    title: String,
    subtitle: String?,
    trailingText: String?,
    showChevron: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 60.dp else 54.dp)
            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
            .hoverable(interactionSource)
            .hand()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
            }
        }
        if (trailingText != null) {
            OutlinedButton(
                onClick = onClick,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp).hand(),
            ) { Text(trailingText) }
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.secondaryLabel,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 60.dp else 54.dp)
            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else Color.Transparent)
            .hoverable(interactionSource)
            .hand()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel)
            }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = accent.color.copy(alpha = 0.45f),
                checkedThumbColor = accent.color,
                uncheckedTrackColor = palette.fill,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SeekSettingRow(
    label: String,
    fraction: Float,
    display: String,
    onFractionChange: (Float) -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(116.dp))
        SeekBar(
            fraction = fraction,
            onSeek = onFractionChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            display,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = palette.secondaryLabel,
            modifier = Modifier.width(40.dp),
        )
    }
}

private class PendingConfirm(val title: String, val text: String, val action: suspend () -> Unit)

private data class RelocateResult(
    val moved: Int,
    val skipped: Int,
    val names: Set<String>,
    val nested: Boolean,
    val same: Boolean,
)

private suspend fun relocateConvertLibrary(db: RabBeatDatabase, from: File, to: File): RelocateResult {
    val fromNorm = from.absoluteFile.normalize()
    val toNorm = to.absoluteFile.normalize()
    if (fromNorm == toNorm) return RelocateResult(0, 0, emptySet(), nested = false, same = true)
    if (toNorm.toPath().startsWith(fromNorm.toPath())) {
        return RelocateResult(0, 0, emptySet(), nested = true, same = false)
    }
    val children = fromNorm.listFiles()?.toList().orEmpty()
    toNorm.mkdirs()
    val movedNames = mutableSetOf<String>()
    var skipped = 0
    children.forEach { child ->
        val dest = File(toNorm, child.name)
        if (dest.exists()) {
            skipped++
            return@forEach
        }
        val landed = runCatching {
            if (!child.renameTo(dest)) {
                child.copyRecursively(dest, overwrite = false)
                if (dest.exists()) child.deleteRecursively()
            }
            dest.exists()
        }.getOrDefault(false)
        if (landed) movedNames += child.name else skipped++
    }
    if (movedNames.isNotEmpty()) retargetMovedTracks(db, fromNorm, toNorm, movedNames)
    return RelocateResult(movedNames.size, skipped, movedNames, nested = false, same = false)
}

private suspend fun retargetMovedTracks(db: RabBeatDatabase, from: File, to: File, movedNames: Set<String>) {
    val tracks = db.trackDao().query(RoomRawQuery("SELECT * FROM tracks"))
    tracks.forEach { track ->
        rewrittenFile(track.uri, from, to, movedNames)?.let { dest ->
            db.trackDao().updateUri(track.id, dest.toURI().toString())
        }
        val lyric = track.lyricUri
        if (!lyric.isNullOrBlank()) {
            rewrittenFile(lyric, from, to, movedNames)?.let { dest ->
                db.trackDao().updateLyricUri(track.id, dest.toURI().toString())
            }
        }
    }
}

private fun rewrittenFile(uri: String, from: File, to: File, movedNames: Set<String>): File? {
    val file = runCatching { File(URI(uri)) }.getOrNull() ?: return null
    val rel = relativeUnder(from, file) ?: return null
    val top = rel.substringBefore('\\').substringBefore('/')
    if (top !in movedNames) return null
    val dest = File(to, rel)
    return dest.takeIf { it.exists() }
}

private fun relativeUnder(root: File, file: File): String? {
    val rootPath = root.absoluteFile.normalize().toPath()
    val filePath = file.absoluteFile.normalize().toPath()
    if (!filePath.startsWith(rootPath)) return null
    val rel = rootPath.relativize(filePath).toString()
    if (rel.isEmpty() || rel == ".." || rel.startsWith("..\\") || rel.startsWith("../")) return null
    return rel
}

private fun applyConvertMove(
    settings: DesktopSettings,
    player: DesktopPlayerController,
    from: File,
    to: File,
    outcome: RelocateResult,
    useDefault: Boolean,
): String {
    if (outcome.nested) return "新目录不能放在当前转换目录里面"
    if (outcome.same) return "目录没有变化"
    if (useDefault) settings.convertDir = "" else settings.convertDir = to.absolutePath
    if (outcome.names.isNotEmpty()) {
        player.remapUris { uri ->
            rewrittenFile(uri, from, to, outcome.names)?.toURI()?.toString() ?: uri
        }
    }
    return when {
        outcome.moved == 0 && outcome.skipped == 0 -> "转换歌曲目录已更改"
        outcome.skipped == 0 -> "已把 ${outcome.moved} 个文件搬到新目录"
        else -> "已搬动 ${outcome.moved} 个文件，${outcome.skipped} 个因目标已存在而留下"
    }
}

private val artMoveLock = Mutex()

/**
 * Moves cover files and saved lyrics into the current cache directory and rewrites stored paths.
 * [previousArt] and [previousLyrics] are the folders from before the cache directory changed.
 * Returns a mapper for artwork URIs already held in memory.
 */
internal suspend fun relocateCacheExtras(
    db: RabBeatDatabase,
    settings: DesktopSettings,
    previousArt: File? = null,
    previousLyrics: File? = null,
): (String?) -> String? = artMoveLock.withLock {
    val artRewrite = relocateFolder(
        dest = settings.artRoot(),
        sources = listOfNotNull(previousArt, File(LibraryScanner.dataDir(), "art")),
        forceRewrite = previousArt != null,
    )
    val lyricRewrite = relocateFolder(
        dest = settings.lyricRoot(),
        sources = listOfNotNull(previousLyrics, File(LibraryScanner.dataDir(), "lyrics")),
        forceRewrite = previousLyrics != null,
    )
    if (artRewrite != null) {
        db.trackDao().query(RoomRawQuery("SELECT * FROM tracks")).forEach { track ->
            val nextArt = artRewrite(track.artworkUri)
            if (nextArt != track.artworkUri) db.trackDao().updateArtworkUri(track.id, nextArt)
            val nextLyric = lyricRewrite?.invoke(track.lyricUri)
            if (nextLyric != null && nextLyric != track.lyricUri) db.trackDao().updateLyricUri(track.id, nextLyric)
        }
        db.albumDao().getAll().forEach { album ->
            val next = artRewrite(album.artworkUri)
            if (next != album.artworkUri) db.albumDao().updateArtworkUri(album.id, next)
        }
        db.artistDao().getAll().forEach { artist ->
            val next = artRewrite(artist.artworkUri)
            if (next != artist.artworkUri) db.artistDao().updateArtworkUri(artist.id, next)
        }
        db.playlistDao().getAll().forEach { playlist ->
            val next = artRewrite(playlist.coverUri)
            if (next != playlist.coverUri) db.playlistDao().setCover(playlist.id, next)
        }
        val bg = artRewrite(settings.bgImage)
        if (bg != null && bg != settings.bgImage) settings.bgImage = bg
    } else if (lyricRewrite != null) {
        db.trackDao().query(RoomRawQuery("SELECT * FROM tracks")).forEach { track ->
            val nextLyric = lyricRewrite(track.lyricUri)
            if (nextLyric != track.lyricUri) db.trackDao().updateLyricUri(track.id, nextLyric)
        }
    }
    return@withLock artRewrite ?: { value -> value }
}

/** Moves children into [dest]. Returns a path rewriter when anything had to be retargeted. */
private fun relocateFolder(
    dest: File,
    sources: List<File>,
    forceRewrite: Boolean,
): ((String?) -> String?)? {
    val target = dest.absoluteFile.normalize()
    val fromDirs = sources.map { it.absoluteFile.normalize() }.distinct().filter { it != target }
    val hadSource = fromDirs.any { it.isDirectory }
    target.mkdirs()
    fromDirs.forEach { from ->
        if (!from.isDirectory) return@forEach
        from.listFiles()?.forEach { child ->
            val landed = File(target, child.name)
            if (landed.exists()) return@forEach
            runCatching {
                if (!child.renameTo(landed)) {
                    child.copyRecursively(landed, overwrite = false)
                    if (landed.exists()) child.deleteRecursively()
                }
            }
        }
        if (from.listFiles().isNullOrEmpty()) from.delete()
    }
    if (!forceRewrite && !hadSource) return null
    return { value ->
        if (value.isNullOrBlank()) value
        else {
            val file = when {
                value.startsWith("file:") -> runCatching { File(URI(value)) }.getOrNull()
                else -> File(value)
            }
            val parent = file?.absoluteFile?.parentFile?.normalize()
            if (file == null || parent == null || fromDirs.none { it == parent }) {
                value
            } else {
                val landed = File(target, file.name)
                if (!landed.isFile) value
                else if (value.startsWith("file:")) landed.toURI().toString()
                else landed.absolutePath
            }
        }
    }
}

private fun moveCacheContents(from: File, to: File) {
    val files = from.listFiles() ?: return
    to.mkdirs()
    files.forEach { f ->
        val dest = File(to, f.name)
        if (!dest.exists()) {
            runCatching {
                if (!f.renameTo(dest)) {
                    f.copyRecursively(dest, overwrite = false)
                    f.deleteRecursively()
                }
            }
        }
    }
}

private fun fmtBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}


@Composable
fun HiddenSongsView(db: RabBeatDatabase, onBack: () -> Unit) {
    val hidden by db.trackDao()
        .observeQuery(TrackQuerySql.select(TrackQuery(hiddenOnly = true)))
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val palette = LocalRabBeatPalette.current
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(palette.fill)
                    .hand()
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("已隐藏的歌曲", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("${hidden.size} 首", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        )
        if (hidden.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(message = "没有隐藏的歌曲")
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(hidden, key = { it.id }) { track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(track.artworkUri, track.title, Modifier.size(40.dp), 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${track.artist} · ${track.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.secondaryLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            OutlinedButton(
                                onClick = { scope.launch { db.trackDao().setHidden(listOf(track.id), false) } },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).hand(),
                            ) { Text("恢复", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 8.dp),
                )
            }
        }
    }
}
