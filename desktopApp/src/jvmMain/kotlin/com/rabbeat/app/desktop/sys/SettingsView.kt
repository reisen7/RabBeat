package com.rabbeat.app.desktop.sys
import com.rabbeat.app.desktop.player.*
import com.rabbeat.app.desktop.library.*
import com.rabbeat.app.desktop.playlist.*
import com.rabbeat.app.desktop.net.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.ui.components.EmptyState
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

    val accentNames = mapOf(
        "ROSE" to "玫瑰粉", "AMBER" to "蜜橙", "MINT" to "薄荷绿",
        "OCEAN" to "湖蓝", "VIOLET" to "薰衣紫",
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }

        SectionLabel("外观")
        listOf("SYSTEM" to "跟随系统", "DARK" to "深色", "LIGHT" to "浅色").forEach { (mode, label) ->
            SettingRadio(label, settings.themeMode == mode) {
                settings.themeMode = mode; onThemeChange()
            }
        }
        accentNames.forEach { (key, label) ->
            SettingRadio(label, settings.accent == key) {
                settings.accent = key; onThemeChange()
            }
        }

        SectionLabel("背景")
        SettingRow(
            title = if (settings.bgImage.isBlank()) "设置背景图…" else "背景图：${File(settings.bgImage).name}",
            subtitle = null,
        ) {
            pickImage { file ->
                settings.bgImage = file.absolutePath
                onAppearanceChange()
            }
        }
        if (settings.bgImage.isNotBlank()) {
            SliderRow("压暗", settings.dimLevel, 0f..0.8f) {
                settings.dimLevel = it; onAppearanceChange()
            }
            SliderRow("卡片不透明度", settings.cardOpacity, 0.1f..1f) {
                settings.cardOpacity = it; onAppearanceChange()
            }
            SettingRow("清除背景图", null) { settings.bgImage = ""; onAppearanceChange() }
        }

        SectionLabel("曲库")
        SettingRow("已隐藏的歌曲", null, onOpenHidden)
        SettingRow(
            "清理失效歌曲",
            "移除本地文件已不存在的曲库记录",
        ) {
            scope.launch { val n = scanner.prune(); status = "已清理 $n 首失效歌曲" }
        }
        SettingRow("清理在线缓存", "删除已下载的在线曲目缓存") {
            scope.launch {
                val dir = File(LibraryScanner.dataDir(), "cache")
                val freed = dir.listFiles()?.sumOf { it.length() } ?: 0L
                dir.deleteRecursively()
                status = "已释放 ${fmtBytes(freed)}"
            }
        }
        SettingRow("导出数据库备份", "复制曲库数据库到指定位置") {
            pickSave("rabbeat-backup.db") { dest ->
                scope.launch {
                    runCatching {
                        File(LibraryScanner.dataDir(), "rabbeat.db").copyTo(dest, overwrite = true)
                        status = "备份已导出到 ${dest.name}"
                    }.onFailure { status = "导出失败：${it.message}" }
                }
            }
        }
        SettingRow("恢复数据库备份", "选择备份文件覆盖当前库（需重启生效）") {
            pickFile("数据库", "db") { src ->
                scope.launch {
                    runCatching {
                        src.copyTo(File(LibraryScanner.dataDir(), "rabbeat.db"), overwrite = true)
                        status = "已恢复，重启应用生效"
                    }.onFailure { status = "恢复失败：${it.message}" }
                }
            }
        }
        SettingRow("导出日志", "合并最近 7 天运行日志") {
            pickSave("rabbeat-logs-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.log") { dest ->
                DesktopLog.export(dest)
                status = "日志已导出到 ${dest.name}"
            }
        }

        SectionLabel("统计")
        InfoRow("歌曲总数", "$trackCount 首")
        InfoRow("音频占用", fmtBytes(storedBytes))

        SectionLabel("关于")
        InfoRow("版本", DesktopUpdate.CURRENT)
        SettingRow("检查更新", if (checking) "检查中…" else status) {
            if (!checking) scope.launch {
                checking = true
                updateDialog = DesktopUpdate.check()
                if (updateDialog == null) status = "已是最新版本"
                checking = false
            }
        }
    }

    updateDialog?.let { rel ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            title = { Text("发现新版本 ${rel.tag}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(rel.notes.ifBlank { "查看发布页了解详情" }, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    DesktopUpdate.browse(rel.assetUrl ?: rel.pageUrl)
                    updateDialog = null
                }) { Text(if (rel.assetUrl != null) "下载" else "查看") }
            },
            dismissButton = { TextButton(onClick = { updateDialog = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun SettingRadio(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(110.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("已隐藏的歌曲", style = MaterialTheme.typography.titleMedium)
        }
        if (hidden.isEmpty()) {
            EmptyState(message = "没有隐藏的歌曲")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(hidden, key = { it.id }) { track ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${track.artist} · ${track.album}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { scope.launch { db.trackDao().setHidden(listOf(track.id), false) } },
                        ) { Text("恢复") }
                    }
                }
            }
        }
    }
}
