package com.rabbeat.app.ui.screens.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.DownloadItem
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassGroup
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.GroupDivider
import com.rabbeat.app.ui.components.GlassSurface
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.SectionHeader
import com.rabbeat.app.ui.components.SettingRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.util.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadPage(val items: List<DownloadItem>, val wifiOnly: Boolean, val auto: Boolean)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val page = combine(downloads.observeDownloads(), settings.wifiOnlyDownloads, settings.autoOfflineFavorites) { items, wifi, auto ->
        ScreenState.Ready(DownloadPage(items, wifi, auto)) as ScreenState<DownloadPage>
    }.catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun setWifi(value: Boolean) { viewModelScope.launch { settings.setWifiOnlyDownloads(value) } }
    fun setAuto(value: Boolean) { viewModelScope.launch { settings.setAutoOfflineFavorites(value) } }
    fun clear() { viewModelScope.launch { downloads.clearCompleted() } }
}

@Composable
fun DownloadsScreen(onBack: () -> Unit, vm: DownloadsViewModel = hiltViewModel()) {
    val state by vm.page.collectAsStateWithLifecycle()
    val accent = LocalAccentPalette.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.downloads_title),
            subtitle = stringResource(R.string.settings_downloads_body),
            leading = {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
            trailing = {
                GlassPill(
                    text = stringResource(R.string.downloads_clear),
                    onClick = vm::clear,
                    icon = Icons.Filled.TaskAlt,
                )
            },
        )
        when (val current = state) {
            ScreenState.Loading -> LoadingState(Modifier.weight(1f).fillMaxWidth())
            ScreenState.Failed -> FailedState(Modifier.weight(1f).fillMaxWidth()) { }
            ScreenState.Empty -> EmptyState(stringResource(R.string.empty_downloads), Modifier.weight(1f).fillMaxWidth())
            is ScreenState.Ready -> DownloadBody(current.value, vm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DownloadBody(page: DownloadPage, vm: DownloadsViewModel, modifier: Modifier = Modifier) {
    val done = page.items.filter { it.state == DownloadState.DOWNLOADED }
    val active = page.items.filter { it.state != DownloadState.DOWNLOADED }
    val bytes = done.sumOf { it.sizeBytes }
    val accent = LocalAccentPalette.current
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            GlassSurface(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = accent.color,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.downloads_summary, done.size, formatBytes(bytes)),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (active.isEmpty()) {
                                stringResource(R.string.downloads_idle)
                            } else {
                                stringResource(R.string.downloads_active_count, active.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GlassPill(
                        text = if (page.wifiOnly) "Wi-Fi" else stringResource(R.string.downloads_network_any),
                        icon = Icons.Filled.CloudDownload,
                        active = page.wifiOnly,
                    )
                }
            }
        }
        item { SectionHeader(stringResource(R.string.downloads_preferences)) }
        item {
            GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(
                    icon = Icons.Filled.CloudDownload,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                    title = stringResource(R.string.downloads_wifi),
                    subtitle = stringResource(R.string.downloads_wifi_body),
                    trailing = {
                        Switch(
                            checked = page.wifiOnly,
                            onCheckedChange = vm::setWifi,
                            colors = SwitchDefaults.colors(checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759), checkedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedTrackColor = com.rabbeat.app.ui.theme.LocalRabBeatPalette.current.fill, uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    },
                )
                GroupDivider()
                SettingRow(
                    icon = Icons.Filled.DownloadDone,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                    title = stringResource(R.string.downloads_auto),
                    subtitle = stringResource(R.string.downloads_auto_hint),
                    trailing = {
                        Switch(
                            checked = page.auto,
                            onCheckedChange = vm::setAuto,
                            colors = SwitchDefaults.colors(checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759), checkedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedTrackColor = com.rabbeat.app.ui.theme.LocalRabBeatPalette.current.fill, uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
                        )
                    },
                )
            }
        }
        if (page.wifiOnly) {
            item {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    GlassPill(stringResource(R.string.downloads_wifi_blocked), active = true)
                }
            }
        }
        if (active.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.downloads_active)) }
            items(active, key = { it.trackId }) { item -> DownloadCard(item) }
        }
        if (done.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.downloads_done)) }
            items(done, key = { it.trackId }) { item -> DownloadCard(item) }
        }
        if (active.isEmpty() && done.isEmpty()) {
            item { EmptyState(stringResource(R.string.empty_downloads), Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem) {
    val accent = LocalAccentPalette.current
    val palette = com.rabbeat.app.ui.theme.LocalRabBeatPalette.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                )
                if (item.state == DownloadState.DOWNLOADING || item.state == DownloadState.QUEUED) {
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent.color,
                        trackColor = palette.fill,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            GlassPill(
                text = stateLabel(item.state),
                active = item.state == DownloadState.DOWNLOADED,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp)
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}

@Composable
private fun stateLabel(state: DownloadState): String = when (state) {
    DownloadState.QUEUED -> stringResource(R.string.state_queued)
    DownloadState.DOWNLOADING -> stringResource(R.string.state_downloading)
    DownloadState.DOWNLOADED -> stringResource(R.string.state_downloaded)
    DownloadState.FAILED -> stringResource(R.string.state_failed)
    DownloadState.NONE -> stringResource(R.string.state_none)
}
