package com.rabbeat.app.desktop

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.ui.components.EmptyState
import kotlinx.coroutines.launch

@Composable
fun SettingsView(
    settings: DesktopSettings,
    onThemeChange: () -> Unit,
    onOpenHidden: () -> Unit,
    onBack: () -> Unit,
) {
    val accentNames = mapOf(
        "ROSE" to "玫瑰粉", "AMBER" to "蜜橙", "MINT" to "薄荷绿",
        "OCEAN" to "湖蓝", "VIOLET" to "薰衣紫",
    )
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }

        Text("外观", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        listOf("SYSTEM" to "跟随系统", "DARK" to "深色", "LIGHT" to "浅色").forEach { (mode, label) ->
            SettingRadio(label, settings.themeMode == mode) {
                settings.themeMode = mode; onThemeChange()
            }
        }

        Text("主题色", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        accentNames.forEach { (key, label) ->
            SettingRadio(label, settings.accent == key) {
                settings.accent = key; onThemeChange()
            }
        }

        Text("曲库", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenHidden).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("已隐藏的歌曲", style = MaterialTheme.typography.bodyMedium)
        }
    }
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
                        androidx.compose.material3.TextButton(
                            onClick = { scope.launch { db.trackDao().setHidden(listOf(track.id), false) } },
                        ) { Text("恢复") }
                    }
                }
            }
        }
    }
}
