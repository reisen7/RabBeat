package com.rabbeat.app.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.data.local.LocalMediaScanner
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.SyncSummary
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.domain.repository.SourceRepository
import com.rabbeat.app.ui.components.AccentButton
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.GlassSurface
import com.rabbeat.app.ui.theme.LocalAccentPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingPhase { Choose, Scan, Server, Done, Failed }

enum class OnboardingNote { TestOk, TestFail, Permission, Empty, Device }

data class OnboardingUi(
    val phase: OnboardingPhase = OnboardingPhase.Choose,
    val serverType: SourceType = SourceType.SUBSONIC,
    val url: String = "https://music.home.local",
    val username: String = "rabbit",
    val password: String = "",
    val progress: Float = 0f,
    val summary: SyncSummary? = null,
    val note: OnboardingNote? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val scanner: LocalMediaScanner,
    private val sources: SourceRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui = _ui.asStateFlow()

    fun showServer() = _ui.update { it.copy(phase = OnboardingPhase.Server, note = null) }
    fun showChoose() = _ui.update { it.copy(phase = OnboardingPhase.Choose, note = null, summary = null) }
    fun updateUrl(value: String) = _ui.update { it.copy(url = value) }
    fun updateUser(value: String) = _ui.update { it.copy(username = value) }
    fun updatePassword(value: String) = _ui.update { it.copy(password = value) }
    fun updateType(type: SourceType) = _ui.update { it.copy(serverType = type) }

    fun scan(granted: Boolean) {
        viewModelScope.launch {
            if (!granted) {
                _ui.update { it.copy(phase = OnboardingPhase.Choose, progress = 0f, note = OnboardingNote.Permission) }
                return@launch
            }
            _ui.update { it.copy(phase = OnboardingPhase.Scan, progress = 0.35f, note = null) }
            val summary = scanner.importDeviceLibrary()
            settings.setOnboardingDone(true)
            _ui.update {
                it.copy(
                    phase = OnboardingPhase.Done,
                    progress = 1f,
                    summary = summary,
                    note = if (summary.trackCount == 0) OnboardingNote.Empty else OnboardingNote.Device,
                )
            }
        }
    }

    fun later(onFinished: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onFinished()
        }
    }

    fun test() {
        viewModelScope.launch {
            val state = _ui.value
            val result = sources.testDraft(state.url, state.username, state.password)
            _ui.update { it.copy(note = if (result.isSuccess) OnboardingNote.TestOk else OnboardingNote.TestFail) }
        }
    }

    fun save(onFinished: () -> Unit) {
        viewModelScope.launch {
            val state = _ui.value
            _ui.update { it.copy(phase = OnboardingPhase.Scan, progress = 0.3f) }
            val id = UUID.randomUUID().toString()
            val added = sources.addSource(
                MusicSource(
                    id = id,
                    type = state.serverType,
                    displayName = state.url.substringAfter("://").substringBefore("/").ifBlank { "服务器" },
                    status = SourceStatus.DISCONNECTED,
                    lastSyncAt = null,
                    createdAt = System.currentTimeMillis(),
                    baseUrl = state.url,
                    username = state.username,
                    password = state.password,
                ),
            )
            if (added.isFailure) {
                _ui.update { it.copy(phase = OnboardingPhase.Failed) }
                return@launch
            }
            if (sources.testConnection(id).isFailure) {
                _ui.update { it.copy(phase = OnboardingPhase.Server, note = OnboardingNote.TestFail) }
                return@launch
            }
            val summary = sources.syncSource(id).getOrElse {
                _ui.update { it.copy(phase = OnboardingPhase.Failed) }
                return@launch
            }
            settings.setOnboardingDone(true)
            _ui.update { it.copy(phase = OnboardingPhase.Done, progress = 1f, summary = summary) }
        }
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val accent = LocalAccentPalette.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.scan(granted)
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.onboarding_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPill(stringResource(R.string.onboarding_tag_offline), icon = Icons.Filled.LibraryMusic)
            GlassPill(stringResource(R.string.onboarding_tag_private), icon = Icons.Filled.Cloud)
        }
        Spacer(Modifier.height(4.dp))

        when (ui.phase) {
            OnboardingPhase.Choose -> {
                SourceCard(
                    icon = Icons.Filled.LibraryMusic,
                    title = stringResource(R.string.source_local),
                    body = stringResource(R.string.source_local_body),
                    primary = true,
                ) { launcher.launch(permission) }
                SourceCard(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    title = stringResource(R.string.source_later),
                    body = stringResource(R.string.source_later_body),
                ) { vm.later(onFinished) }
                NoteText(ui.note)
            }

            OnboardingPhase.Scan -> {
                GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.scan_running), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { ui.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent.color,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        )
                        Spacer(Modifier.height(10.dp))
                        NoteText(ui.note)
                    }
                }
            }

            OnboardingPhase.Server -> {
                GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.source_server), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.server_type), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(SourceType.SUBSONIC, SourceType.WEBDAV, SourceType.SMB).forEach { type ->
                                GlassPill(
                                    text = typeLabel(type),
                                    active = ui.serverType == type,
                                    onClick = { vm.updateType(type) },
                                )
                            }
                        }
                        OutlinedTextField(
                            ui.url,
                            vm::updateUrl,
                            label = { Text(stringResource(R.string.server_url)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        OutlinedTextField(
                            ui.username,
                            vm::updateUser,
                            label = { Text(stringResource(R.string.server_user)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            ui.password,
                            vm::updatePassword,
                            label = { Text(stringResource(R.string.server_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        NoteText(ui.note)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassPill(
                                text = stringResource(R.string.action_test),
                                onClick = vm::test,
                            )
                            AccentButton(stringResource(R.string.action_save_sync), { vm.save(onFinished) }, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = vm::showChoose) { Text(stringResource(R.string.action_back)) }
                    }
                }
            }

            OnboardingPhase.Done -> {
                val summary = ui.summary
                GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = accent.color,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.scan_done, summary?.trackCount ?: 0),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassPill(stringResource(R.string.scan_albums, summary?.albumCount ?: 0))
                            GlassPill(stringResource(R.string.scan_missing, summary?.missingMetadata ?: 0))
                        }
                        Spacer(Modifier.height(16.dp))
                        NoteText(ui.note)
                        Spacer(Modifier.height(10.dp))
                        AccentButton(stringResource(R.string.action_enter), onFinished, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            OnboardingPhase.Failed -> FailedState(Modifier.fillMaxWidth().height(240.dp)) { vm.showChoose() }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SourceCard(
    icon: ImageVector,
    title: String,
    body: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    GlassSurface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent.color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoteText(note: OnboardingNote?) {
    val text = when (note) {
        OnboardingNote.TestOk -> stringResource(R.string.test_ok)
        OnboardingNote.TestFail -> stringResource(R.string.test_fail)
        OnboardingNote.Permission -> stringResource(R.string.permission_denied)
        OnboardingNote.Empty -> stringResource(R.string.scan_empty)
        OnboardingNote.Device -> stringResource(R.string.scan_device_note)
        null -> return
    }
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun typeLabel(type: SourceType): String = when (type) {
    SourceType.LOCAL -> stringResource(R.string.type_local)
    SourceType.SUBSONIC -> stringResource(R.string.type_subsonic)
    SourceType.WEBDAV -> stringResource(R.string.type_webdav)
    SourceType.SMB -> stringResource(R.string.type_smb)
    SourceType.NETEASE -> stringResource(R.string.type_netease)
}
