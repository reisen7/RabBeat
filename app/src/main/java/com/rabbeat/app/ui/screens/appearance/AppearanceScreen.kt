package com.rabbeat.app.ui.screens.appearance

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.data.local.BackgroundImageStore
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.ui.components.AccentButton
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.GlassGroup
import com.rabbeat.app.ui.components.GroupDivider
import com.rabbeat.app.ui.components.GroupHeader
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.GlassSurface
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.PillTabRow
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.SettingRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import com.rabbeat.app.ui.theme.palette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backgroundStore: BackgroundImageStore,
) : ViewModel() {
    val appearance = settings.appearance.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppearanceSettings(),
    )
    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    fun setAccent(accent: AccentTheme) = viewModelScope.launch { settings.setAccentTheme(accent) }
    fun stageBackground(uri: Uri, onReady: (File?) -> Unit) = viewModelScope.launch {
        onReady(withContext(Dispatchers.IO) { backgroundStore.importSource(uri) })
    }
    fun applyCropped(bitmap: Bitmap) = viewModelScope.launch {
        val saved = withContext(Dispatchers.IO) { backgroundStore.saveCropped(bitmap) } ?: return@launch
        withContext(Dispatchers.IO) { backgroundStore.promotePending() }
        backgroundStore.release(appearance.value.backgroundUri)
        settings.setBackgroundUri(saved)
        settings.setBackgroundEnabled(true)
    }
    fun discardPending() = viewModelScope.launch {
        withContext(Dispatchers.IO) { backgroundStore.discardPending() }
    }
    fun sourceFile(): File? = backgroundStore.sourceFile()
    suspend fun hasSourceFile(): Boolean = withContext(Dispatchers.IO) { backgroundStore.sourceFile() != null }
    fun removeBackground() = viewModelScope.launch {
        backgroundStore.clearAll()
        settings.setBackgroundUri(null)
    }
    fun setEnabled(enabled: Boolean) = viewModelScope.launch { settings.setBackgroundEnabled(enabled) }
    fun setBlur(value: Float) = viewModelScope.launch { settings.setBackgroundBlur(value) }
    fun setDim(value: Float) = viewModelScope.launch { settings.setBackgroundDim(value) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun reset() = viewModelScope.launch {
        backgroundStore.clearAll()
        settings.setAccentTheme(AccentTheme.ROSE)
        settings.setBackgroundUri(null)
        settings.setBackgroundEnabled(true)
        settings.setBackgroundBlur(22f)
        settings.setBackgroundDim(0.55f)
    }
}

@Composable
fun AppearanceScreen(onBack: () -> Unit, vm: AppearanceViewModel = hiltViewModel()) {
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cropSource by remember { mutableStateOf<File?>(null) }
    var hasSource by remember { mutableStateOf(false) }
    LaunchedEffect(appearance.backgroundUri) { hasSource = vm.hasSourceFile() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.stageBackground(uri) { file ->
                if (file != null) {
                    cropSource = file
                } else {
                    Toast.makeText(context, R.string.crop_open_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(com.rabbeat.app.ui.components.groupedPageBackground())) {
        ScreenHeader(
            title = stringResource(R.string.appearance_title),
            leading = {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp),
        ) {
            LivePreview(appearance)

            GroupHeader(stringResource(R.string.appearance_background))
            GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                SettingRow(
                    icon = Icons.Filled.Wallpaper,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.appearance_pick),
                    subtitle = if (appearance.backgroundUri == null) {
                        stringResource(R.string.appearance_no_image)
                    } else {
                        stringResource(R.string.appearance_image_ready)
                    },
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    trailing = {
                        GlassPill(
                            text = if (appearance.backgroundUri == null) {
                                stringResource(R.string.appearance_choose)
                            } else {
                                stringResource(R.string.appearance_change)
                            },
                            active = true,
                        )
                    },
                )
                GroupDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.appearance_enable), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.appearance_enable_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = appearance.backgroundEnabled && appearance.backgroundUri != null,
                        enabled = appearance.backgroundUri != null,
                        onCheckedChange = vm::setEnabled,
                        colors = IosSwitchColors(),
                    )
                }
                if (appearance.backgroundUri != null && hasSource) {
                    GroupDivider()
                    SettingRow(
                        icon = Icons.Filled.Crop,
                        iconColor = Color(0xFF34C759),
                        title = stringResource(R.string.appearance_recrop),
                        subtitle = stringResource(R.string.appearance_recrop_body),
                        onClick = { vm.sourceFile()?.let { cropSource = it } },
                    )
                }
                if (appearance.backgroundUri != null) {
                    GlassPill(
                        text = stringResource(R.string.appearance_remove),
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 8.dp),
                        onClick = vm::removeBackground,
                    )
                }
                SliderSetting(
                    label = stringResource(R.string.appearance_blur),
                    valueLabel = "${appearance.backgroundBlur.toInt()} dp",
                    value = appearance.backgroundBlur,
                    range = 0f..40f,
                    enabled = appearance.backgroundUri != null && appearance.backgroundEnabled,
                    onValueChange = vm::setBlur,
                )
                SliderSetting(
                    label = stringResource(R.string.appearance_dim),
                    valueLabel = "${(appearance.backgroundDim * 100).toInt()}%",
                    value = appearance.backgroundDim,
                    range = 0f..0.9f,
                    enabled = appearance.backgroundUri != null && appearance.backgroundEnabled,
                    onValueChange = vm::setDim,
                )
                Spacer(Modifier.height(6.dp))
            }

            GroupHeader(stringResource(R.string.appearance_accent))
            GlassGroup(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    AccentTheme.entries.forEach { entry ->
                        val color = entry.paletteColor()
                        val selected = appearance.accent == entry
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) color else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .padding(if (selected) 4.dp else 3.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { vm.setAccent(entry) },
                        )
                    }
                }
            }

            GroupHeader(stringResource(R.string.appearance_theme))
            PillTabRow(
                tabs = ThemeMode.entries.map { themeLabel(it) },
                selectedIndex = ThemeMode.entries.indexOf(theme),
                onSelected = { vm.setTheme(ThemeMode.entries[it]) },
            )

            GroupHeader(stringResource(R.string.appearance_reset))
            Box(Modifier.padding(horizontal = 16.dp)) {
                GlassPill(
                    text = stringResource(R.string.appearance_reset_action),
                    icon = Icons.Filled.RestartAlt,
                    onClick = vm::reset,
                )
            }
        }
    }
    cropSource?.let { file ->
        BackgroundCropper(
            source = file,
            onDone = { bitmap ->
                vm.applyCropped(bitmap)
                cropSource = null
            },
            onDismiss = {
                vm.discardPending()
                cropSource = null
            },
        )
    }
}

@Composable
private fun LivePreview(appearance: AppearanceSettings) {
    val accent = LocalAccentPalette.current
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(null, "RabBeat", Modifier.size(74.dp), 10.dp)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.appearance_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.appearance_preview_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .height(4.dp)
                                .weight(1f)
                                .clip(CircleShape)
                                .background(accent.color),
                        )
                        Box(
                            Modifier
                                .height(4.dp)
                                .weight(2.2f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(accent.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("▶", color = accent.onColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = LocalAccentPalette.current.color,
                inactiveTrackColor = LocalRabBeatPalette.current.fill,
            ),
        )
    }
}

@Composable
private fun AccentTheme.paletteColor(): Color =
    palette(LocalRabBeatPalette.current.isDark).color

@Composable
private fun IosSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = Color(0xFF34C759),
    checkedThumbColor = Color.White,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = LocalRabBeatPalette.current.fill,
    uncheckedBorderColor = Color.Transparent,
)

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
}

