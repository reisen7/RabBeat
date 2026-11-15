package com.rabbeat.app.desktop.sys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.desktop.library.LibraryScanner
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    settings: DesktopSettings,
    activeThemeMode: String = settings.themeMode,
    scanner: LibraryScanner,
    db: RabBeatDatabase,
    onThemeChange: () -> Unit,
    onAppearanceChange: () -> Unit,
    onOpenHidden: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val trackCount by db.trackDao().observeCount(
        TrackQuerySql.select(TrackQuery()),
    ).collectAsState(initial = 0)
    val storedBytes by db.trackDao().observeStoredBytes().collectAsState(initial = 0L)
    var updateDialog by remember { mutableStateOf<DesktopUpdate.Result?>(null) }
    var checking by remember { mutableStateOf(false) }

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
                        modifier = Modifier.weight(1f),
                    )
                    status?.let {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(accent.soft)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = accent.color)
                        }
                    }
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
                                if (settings.bgImage.isBlank()) "未设置自定义背景图" else File(settings.bgImage).name,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (settings.bgImage.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    settings.bgImage = ""
                                    onAppearanceChange()
                                },
                                modifier = Modifier.hand(),
                            ) { Text("清除") }
                            Spacer(Modifier.width(6.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                pickImage { file ->
                                    settings.bgImage = file.absolutePath
                                    onAppearanceChange()
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).hand(),
                        ) { Text("选择图片") }
                    }
                    if (settings.bgImage.isNotBlank()) {
                        CardDivider()
                        SeekSettingRow(
                            label = "背景压暗",
                            fraction = (settings.dimLevel / 0.8f).coerceIn(0f, 1f),
                            display = "${(settings.dimLevel * 100).toInt()}%",
                            onFractionChange = {
                                settings.dimLevel = (it * 0.8f).coerceIn(0f, 0.8f)
                                onAppearanceChange()
                            },
                        )
                        CardDivider()
                        SeekSettingRow(
                            label = "卡片不透明度",
                            fraction = ((settings.cardOpacity - 0.1f) / 0.9f).coerceIn(0f, 1f),
                            display = "${(settings.cardOpacity * 100).toInt()}%",
                            onFractionChange = {
                                settings.cardOpacity = (0.1f + it * 0.9f).coerceIn(0.1f, 1f)
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
                        title = "已隐藏的歌曲",
                        subtitle = "查看或恢复之前隐藏的曲目",
                        trailingText = null,
                        showChevron = true,
                        onClick = onOpenHidden,
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "清理失效歌曲",
                        subtitle = "移除本地文件已不存在的曲库记录",
                        trailingText = "清理",
                        onClick = {
                            scope.launch {
                                val n = scanner.prune()
                                status = "已清理 $n 首失效歌曲"
                            }
                        },
                    )
                    CardDivider()
                    ActionSettingRow(
                        title = "清理在线缓存",
                        subtitle = "删除已下载的在线曲目缓存",
                        trailingText = "清理",
                        onClick = {
                            scope.launch {
                                val dir = File(LibraryScanner.dataDir(), "cache")
                                val freed = dir.listFiles()?.sumOf { it.length() } ?: 0L
                                dir.deleteRecursively()
                                status = "已释放 ${fmtBytes(freed)}"
                            }
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                SectionTitle("数据与更新")
                SettingsCard {
                    ActionSettingRow(
                        title = "导出数据库备份",
                        subtitle = "复制曲库数据库到指定位置",
                        trailingText = "导出",
                        onClick = {
                            pickSave("rabbeat-backup.db") { dest ->
                                scope.launch {
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
                            pickFile("数据库", "db") { src ->
                                scope.launch {
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
                            pickSave("rabbeat-logs-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.log") { dest ->
                                DesktopLog.export(dest)
                                status = "日志已导出到 ${dest.name}"
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
                    Row(
                        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("版本与更新", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "当前版本 ${DesktopUpdate.CURRENT}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.secondaryLabel,
                            )
                        }
                        OutlinedButton(
                            enabled = !checking,
                            onClick = {
                                scope.launch {
                                    checking = true
                                    updateDialog = DesktopUpdate.check()
                                    if (updateDialog == null) status = "已是最新版本"
                                    checking = false
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).hand(),
                        ) {
                            Text(if (checking) "检查中…" else "检查更新")
                        }
                    }
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.fill)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    keyLabel,
                                    style = MaterialTheme.typography.labelSmall,
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
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }

    updateDialog?.let { rel ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            title = { Text("发现新版本 ${rel.tag}", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(rel.notes.ifBlank { "查看发布页了解详情" }, style = MaterialTheme.typography.bodySmall)
                }
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
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(palette.fill)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(trailingText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
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

private fun fmtBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

private fun pickImage(onPicked: (File) -> Unit) =
    pickFile("图片", "jpg", "jpeg", "png", "webp", onPicked = onPicked)

private fun pickFile(desc: String, vararg ext: String, onPicked: (File) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser(javax.swing.filechooser.FileSystemView.getFileSystemView().homeDirectory)
        chooser.fileFilter = FileNameExtensionFilter(desc, *ext)
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) onPicked(chooser.selectedFile)
    }
}

private fun pickSave(defaultName: String, onPicked: (File) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser(javax.swing.filechooser.FileSystemView.getFileSystemView().homeDirectory)
        chooser.selectedFile = File(chooser.currentDirectory, defaultName)
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) onPicked(chooser.selectedFile)
    }
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
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}
