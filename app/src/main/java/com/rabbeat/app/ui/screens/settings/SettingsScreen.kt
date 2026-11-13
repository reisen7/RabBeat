package com.rabbeat.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.rabbeat.app.R
import com.rabbeat.app.data.local.FolderLibraryImporter
import com.rabbeat.app.data.local.InvalidNeteaseLinkException
import com.rabbeat.app.data.local.LibraryBackup
import com.rabbeat.app.data.local.LocalMediaScanner
import com.rabbeat.app.data.local.NeteaseImportResult
import com.rabbeat.app.data.local.NeteasePlaylistImporter
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.playback.MediaCache
import com.rabbeat.app.playback.NeteaseCacheStatus
import com.rabbeat.app.util.ScanProgress
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.ScanSettings
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.domain.repository.SourceRepository
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassGroup
import com.rabbeat.app.ui.components.GroupDivider
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.GroupHeader
import com.rabbeat.app.ui.components.SettingRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.util.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsPage(
    val theme: ThemeMode,
    val sources: List<MusicSource>,
    val bytes: Long,
    val sleepMinutes: Int,
    val scan: ScanSettings,
)

private enum class ScanEdit { SIZE, DURATION }

sealed interface NeteaseImport {
    data object Idle : NeteaseImport
    data class Running(val done: Int, val total: Int) : NeteaseImport
    data class Done(val result: NeteaseImportResult) : NeteaseImport
    data object BadLink : NeteaseImport
    data object Failed : NeteaseImport
}

enum class ScanKind { Device, Folder, Rescan }

data class ScanTask(val kind: ScanKind, val done: Int = 0, val total: Int = 0)

private const val SCAN_FAILED = -1
private const val SCAN_CANCELLED = -2

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    sources: SourceRepository,
    trackDao: TrackDao,
    private val scanner: LocalMediaScanner,
    private val folders: FolderLibraryImporter,
    private val backup: LibraryBackup,
    private val neteaseImporter: NeteasePlaylistImporter,
    private val mediaCache: MediaCache,
    private val cacheStatus: NeteaseCacheStatus,
    private val logger: com.rabbeat.app.util.AppLogger,
    private val updateChecker: com.rabbeat.app.util.UpdateChecker,
) : ViewModel() {
    val page = combine(
        settings.themeMode,
        sources.observeSources(),
        trackDao.observeStoredBytes(),
        settings.sleepTimerMinutes,
        settings.scanSettings,
    ) { theme, sourceList, bytes, sleep, scan ->
        ScreenState.Ready(SettingsPage(theme, sourceList, bytes, sleep, scan)) as ScreenState<SettingsPage>
    }.catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun updateScan(transform: (ScanSettings) -> ScanSettings) {
        viewModelScope.launch { settings.updateScanSettings(transform) }
    }

    fun setTheme(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setSleep(minutes: Int) { viewModelScope.launch { settings.setSleepTimerMinutes(minutes) } }
    fun needsAllFilesAccess() = scanner.needsAllFilesAccess()

    private var scanJob: Job? = null

    fun importDevice(onDone: (Int) -> Unit) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            if (!scanner.hasAudioPermission()) {
                onDone(-1)
                return@launch
            }
            runScan(ScanKind.Device, onDone) { report ->
                scanner.importDeviceLibrary(report).trackCount
            }
        }
    }
    fun importFolder(uri: Uri, onDone: (added: Int, resolved: Int) -> Unit) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            var added = 0
            runScan(ScanKind.Folder, { code -> if (code < 0) onDone(code, 0) else onDone(added, code) }) { report ->
                val result = folders.importTreeForPlaylist(uri, report)
                added = result.summary.trackCount
                result.trackIds.size
            }
        }
    }
    fun rescanFolders(onDone: (Int) -> Unit) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            runScan(ScanKind.Rescan, onDone) { report ->
                folders.rescanSaved(report).trackCount
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    private suspend fun runScan(kind: ScanKind, onDone: (Int) -> Unit, work: suspend (ScanProgress) -> Int) {
        scan.value = ScanTask(kind)
        var result = SCAN_CANCELLED
        try {
            result = work { done, total -> scan.value = ScanTask(kind, done.coerceAtLeast(0), total.coerceAtLeast(0)) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            result = SCAN_FAILED
        } finally {
            scan.value = null
            onDone(result)
        }
    }

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(runCatching { backup.export(uri) }.isSuccess) }
    }
    fun exportLogs(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { logger.exportTo(uri) }
            logger.i("Logs", "export ${if (ok) "ok" else "failed"}")
            onDone(ok)
        }
    }
    fun checkUpdate(onDone: (com.rabbeat.app.util.UpdateResult) -> Unit) {
        viewModelScope.launch { onDone(updateChecker.check()) }
    }
    fun restoreBackup(uri: Uri, onDone: (com.rabbeat.app.data.local.RestoreResult?) -> Unit) {
        viewModelScope.launch { onDone(runCatching { backup.restore(uri) }.getOrNull()) }
    }

    val neteaseImport = MutableStateFlow<NeteaseImport>(NeteaseImport.Idle)
    val scan = MutableStateFlow<ScanTask?>(null)
    private var importJob: Job? = null
    val mediaCacheBytes = MutableStateFlow(0L)
    val hiddenCount = trackDao.observeHiddenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun refreshMediaCache() { mediaCacheBytes.value = mediaCache.usedBytes }

    fun startNeteaseImport(text: String) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            neteaseImport.value = NeteaseImport.Running(0, 0)
            neteaseImport.value = try {
                val result = neteaseImporter.import(text) { done, total ->
                    neteaseImport.value = NeteaseImport.Running(done, total)
                }
                NeteaseImport.Done(result)
            } catch (invalid: InvalidNeteaseLinkException) {
                NeteaseImport.BadLink
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                NeteaseImport.Failed
            }
        }
    }

    fun resetNeteaseImport() { neteaseImport.value = NeteaseImport.Idle }

    fun clearMediaCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { mediaCache.clear() }
            cacheStatus.refresh()
            mediaCacheBytes.value = mediaCache.usedBytes
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onDownloads: () -> Unit,
    onHistory: () -> Unit,
    onAppearance: () -> Unit,
    onHidden: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.page.collectAsStateWithLifecycle()
    val neteaseImport by vm.neteaseImport.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val mediaCacheBytes by vm.mediaCacheBytes.collectAsStateWithLifecycle()
    val hiddenCount by vm.hiddenCount.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshMediaCache() }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val denied = stringResource(R.string.permission_denied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            scope.launch { snack.showSnackbar(denied) }
        } else {
            vm.importDevice { count ->
                val message = when {
                    count == SCAN_CANCELLED -> context.getString(R.string.scan_cancelled)
                    count < 0 -> context.getString(R.string.scan_failed)
                    count == 0 -> context.getString(R.string.scan_empty)
                    else -> context.getString(R.string.settings_scan_done, count)
                }
                scope.launch { snack.showSnackbar(message) }
            }
        }
    }
    val cleared = stringResource(R.string.cache_cleared)
    var themeOpen by remember { mutableStateOf(false) }
    var sleepOpen by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var scanEdit by remember { mutableStateOf<ScanEdit?>(null) }
    var restoreConfirm by remember { mutableStateOf(false) }
    var neteaseOpen by remember { mutableStateOf(false) }
    var cacheConfirm by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<com.rabbeat.app.util.UpdateResult?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var allFilesPrompt by remember { mutableStateOf(false) }
    var waitingForAllFiles by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.importFolder(uri) { added, resolved ->
                val message = when {
                    added == SCAN_CANCELLED -> context.getString(R.string.scan_cancelled)
                    added < 0 -> context.getString(R.string.scan_failed)
                    resolved == 0 -> context.getString(R.string.scan_empty)
                    added == 0 -> context.getString(R.string.settings_folder_existing, resolved)
                    added < resolved -> context.getString(R.string.settings_folder_partial, added, resolved - added)
                    else -> context.getString(R.string.settings_folder_done, added)
                }
                scope.launch { snack.showSnackbar(message) }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            vm.exportBackup(uri) { ok ->
                val message = if (ok) context.getString(R.string.settings_backup_done) else context.getString(R.string.settings_backup_failed)
                scope.launch { snack.showSnackbar(message) }
            }
        }
    }
    val logsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            vm.exportLogs(uri) { ok ->
                val message = if (ok) context.getString(R.string.settings_logs_done) else context.getString(R.string.settings_logs_failed)
                scope.launch { snack.showSnackbar(message) }
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.restoreBackup(uri) { result ->
                val message = if (result == null) {
                    context.getString(R.string.settings_backup_failed)
                } else {
                    context.getString(R.string.settings_restore_done, result.playlists, result.missingTracks)
                }
                scope.launch { snack.showSnackbar(message) }
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Coming back from the "all files access" system page: continue the scan we
        // interrupted, so NCM files get picked up without a second tap.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && waitingForAllFiles) {
                waitingForAllFiles = false
                launcher.launch(permission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(com.rabbeat.app.ui.components.groupedPageBackground())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.nav_settings),
                leading = {
                    com.rabbeat.app.ui.components.HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
            )
            when (val current = state) {
                ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
                ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
                ScreenState.Empty -> FailedState(Modifier.fillMaxSize()) { }
                is ScreenState.Ready -> SettingsBody(
                    page = current.value,
                    mediaCacheBytes = mediaCacheBytes,
                    hiddenCount = hiddenCount,
                    onNeteaseImport = { neteaseOpen = true },
                    onMediaCache = { cacheConfirm = true },
                    onAppearance = onAppearance,
                    onTheme = { themeOpen = true },
                    onSleep = { sleepOpen = true },
                    onHistory = onHistory,
                    onDownloads = onDownloads,
                    onScan = {
                        if (vm.needsAllFilesAccess()) {
                            allFilesPrompt = true
                        } else {
                            launcher.launch(permission)
                        }
                    },
                    onImportFolder = { folderLauncher.launch(null) },
                    onRescan = {
                        vm.rescanFolders { count ->
                            val message = when {
                                count == SCAN_CANCELLED -> context.getString(R.string.scan_cancelled)
                                count < 0 -> context.getString(R.string.scan_failed)
                                count == 0 -> context.getString(R.string.scan_empty)
                                else -> context.getString(R.string.settings_folder_done, count)
                            }
                            scope.launch { snack.showSnackbar(message) }
                        }
                    },
                    onExportLogs = {
                        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())
                        logsLauncher.launch("rabbeat-logs-$stamp.zip")
                    },
                    onCheckUpdate = {
                        updateChecking = true
                        vm.checkUpdate { result ->
                            updateChecking = false
                            when (result) {
                                is com.rabbeat.app.util.UpdateResult.Available -> updateResult = result
                                com.rabbeat.app.util.UpdateResult.Latest -> scope.launch {
                                    snack.showSnackbar(context.getString(R.string.update_latest))
                                }
                                com.rabbeat.app.util.UpdateResult.Failed -> scope.launch {
                                    snack.showSnackbar(context.getString(R.string.update_failed))
                                }
                            }
                        }
                    },
                    onBackup = { exportLauncher.launch("rabbeat-backup.json") },
                    onRestore = { restoreConfirm = true },
                    onHidden = onHidden,
                    onClearCache = {
                        SingletonImageLoader.get(context).memoryCache?.clear()
                        SingletonImageLoader.get(context).diskCache?.clear()
                        scope.launch { snack.showSnackbar(cleared) }
                    },
                    onLicenses = { licenses = true },
                    onFilterNonSongs = { enabled -> vm.updateScan { it.copy(filterNonSongs = enabled) } },
                    onSkipSmall = { enabled -> vm.updateScan { it.copy(skipSmallFiles = enabled) } },
                    onSkipShort = { enabled -> vm.updateScan { it.copy(skipShortTracks = enabled) } },
                    onEditSize = { scanEdit = ScanEdit.SIZE },
                    onEditDuration = { scanEdit = ScanEdit.DURATION },
                )
            }
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter))
    }

    val ready = (state as? ScreenState.Ready)?.value
    if (themeOpen && ready != null) {
        AlertDialog(
            onDismissRequest = { themeOpen = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = ready.theme == mode, onClick = { vm.setTheme(mode) })
                            Text(themeLabel(mode))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeOpen = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (sleepOpen && ready != null) {
        AlertDialog(
            onDismissRequest = { sleepOpen = false },
            title = { Text(stringResource(R.string.settings_sleep_default)) },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(onClick = { vm.setSleep(minutes); sleepOpen = false }) {
                            Text(stringResource(R.string.sleep_minutes, minutes))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sleepOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    val editing = scanEdit
    if (editing != null && ready != null) {
        ScanNumberDialog(
            editing = editing,
            initial = if (editing == ScanEdit.SIZE) ready.scan.minSizeKb else ready.scan.minDurationSec,
            onDismiss = { scanEdit = null },
            onConfirm = { number ->
                if (editing == ScanEdit.SIZE) {
                    vm.updateScan { it.copy(minSizeKb = number) }
                } else {
                    vm.updateScan { it.copy(minDurationSec = number) }
                }
                scanEdit = null
            },
        )
    }
    if (licenses) {
        AlertDialog(
            onDismissRequest = { licenses = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = { Text(stringResource(R.string.licenses_body)) },
            confirmButton = { TextButton(onClick = { licenses = false }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (restoreConfirm) {
        AlertDialog(
            onDismissRequest = { restoreConfirm = false },
            title = { Text(stringResource(R.string.settings_restore)) },
            text = { Text(stringResource(R.string.settings_restore_body)) },
            confirmButton = {
                TextButton(onClick = {
                    restoreConfirm = false
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { restoreConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (allFilesPrompt) {
        AlertDialog(
            onDismissRequest = { allFilesPrompt = false },
            title = { Text(stringResource(R.string.scan_all_files_title)) },
            text = { Text(stringResource(R.string.scan_all_files_body)) },
            confirmButton = {
                TextButton(onClick = {
                    allFilesPrompt = false
                    waitingForAllFiles = true
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    )
                    val opened = runCatching { context.startActivity(intent) }.isSuccess
                    if (!opened) {
                        waitingForAllFiles = false
                        launcher.launch(permission)
                    }
                }) { Text(stringResource(R.string.scan_all_files_grant)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    allFilesPrompt = false
                    launcher.launch(permission)
                }) { Text(stringResource(R.string.scan_all_files_skip)) }
            },
        )
    }
    val running = scan
    if (running != null) {
        AlertDialog(
            onDismissRequest = { vm.cancelScan() },
            title = {
                Text(
                    stringResource(
                        when (running.kind) {
                            ScanKind.Device -> R.string.scanning_device
                            ScanKind.Folder -> R.string.scanning_folder
                            ScanKind.Rescan -> R.string.scanning_rescan
                        },
                    ),
                )
            },
            text = {
                Column {
                    if (running.total > 0) {
                        LinearProgressIndicator(
                            progress = { (running.done.toFloat() / running.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (running.total > 0) {
                            stringResource(R.string.scanning_progress, running.done, running.total)
                        } else {
                            stringResource(R.string.scanning_found, running.done)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.cancelScan() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (neteaseOpen) {
        NeteaseImportSheet(
            state = neteaseImport,
            onStart = vm::startNeteaseImport,
            onFinished = { message ->
                neteaseOpen = false
                vm.resetNeteaseImport()
                scope.launch { snack.showSnackbar(message) }
            },
            onDismiss = {
                neteaseOpen = false
                vm.resetNeteaseImport()
            },
        )
    }
    if (cacheConfirm) {
        AlertDialog(
            onDismissRequest = { cacheConfirm = false },
            title = { Text(stringResource(R.string.media_cache_clear_title)) },
            text = { Text(stringResource(R.string.media_cache_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    cacheConfirm = false
                    vm.clearMediaCache()
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { cacheConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (updateChecking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            },
            confirmButton = {},
        )
    }

    val update = updateResult as? com.rabbeat.app.util.UpdateResult.Available
    if (update != null) {
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = { Text(stringResource(R.string.update_available_body, update.tag)) },
            confirmButton = {
                TextButton(onClick = {
                    updateResult = null
                    context.startActivity(Intent(Intent.ACTION_VIEW, update.url.toUri()))
                }) { Text(stringResource(R.string.action_download)) }
            },
            dismissButton = { TextButton(onClick = { updateResult = null }) { Text(stringResource(R.string.action_later)) } },
        )
    }
}

@Composable
private fun SettingsBody(
    page: SettingsPage,
    mediaCacheBytes: Long,
    hiddenCount: Int,
    onNeteaseImport: () -> Unit,
    onMediaCache: () -> Unit,
    onAppearance: () -> Unit,
    onTheme: () -> Unit,
    onSleep: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onScan: () -> Unit,
    onImportFolder: () -> Unit,
    onRescan: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportLogs: () -> Unit,
    onCheckUpdate: () -> Unit,
    onHidden: () -> Unit,
    onClearCache: () -> Unit,
    onLicenses: () -> Unit,
    onFilterNonSongs: (Boolean) -> Unit,
    onSkipSmall: (Boolean) -> Unit,
    onSkipShort: (Boolean) -> Unit,
    onEditSize: () -> Unit,
    onEditDuration: () -> Unit,
) {
    val accent = LocalAccentPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(com.rabbeat.app.ui.components.groupedPageBackground())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp),
    ) {
        GroupHeader(stringResource(R.string.settings_appearance))
        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
            SettingRow(
                icon = Icons.Filled.Palette,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFAF52DE),
                title = stringResource(R.string.settings_personalize_row),
                subtitle = stringResource(R.string.appearance_settings_body),
                onClick = onAppearance,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.DarkMode,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF5856D6),
                title = stringResource(R.string.settings_theme),
                subtitle = themeLabel(page.theme),
                onClick = onTheme,
            )
        }

        GroupHeader(stringResource(R.string.settings_scan_rules))
        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
            SettingRow(
                icon = Icons.Filled.FilterAlt,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                title = stringResource(R.string.scan_filter_nonsong),
                modifier = Modifier.toggleable(page.scan.filterNonSongs, onValueChange = onFilterNonSongs),
                trailing = { ScanSwitch(page.scan.filterNonSongs, null) },
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.SdStorage,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                title = stringResource(R.string.scan_skip_small),
                modifier = Modifier.toggleable(page.scan.skipSmallFiles, onValueChange = onSkipSmall),
                trailing = { ScanSwitch(page.scan.skipSmallFiles, null) },
            )
            if (page.scan.skipSmallFiles) {
                GroupDivider()
                ThresholdRow(
                    stringResource(R.string.scan_edit_size),
                    stringResource(R.string.scan_value_kb, page.scan.minSizeKb),
                    onEditSize,
                )
            }
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.Timer,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFFF9500),
                title = stringResource(R.string.scan_skip_short),
                modifier = Modifier.toggleable(page.scan.skipShortTracks, onValueChange = onSkipShort),
                trailing = { ScanSwitch(page.scan.skipShortTracks, null) },
            )
            if (page.scan.skipShortTracks) {
                GroupDivider()
                ThresholdRow(
                    stringResource(R.string.scan_edit_duration),
                    stringResource(R.string.scan_value_sec, page.scan.minDurationSec),
                    onEditDuration,
                )
            }
        }

        GroupHeader(stringResource(R.string.library_tracks))
        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
            SettingRow(
                icon = Icons.Filled.LibraryMusic,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFFF3B30),
                title = stringResource(R.string.settings_scan_device),
                subtitle = stringResource(R.string.settings_scan_body),
                onClick = onScan,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.FolderOpen,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                title = stringResource(R.string.settings_import_folder),
                subtitle = stringResource(R.string.settings_folder_body),
                onClick = onImportFolder,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.Refresh,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                title = stringResource(R.string.settings_rescan_folders),
                subtitle = stringResource(R.string.settings_rescan_body),
                onClick = onRescan,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.CloudDownload,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFE60026),
                title = stringResource(R.string.settings_netease_import),
                subtitle = stringResource(R.string.settings_netease_import_body),
                onClick = onNeteaseImport,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.Save,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF5856D6),
                title = stringResource(R.string.settings_backup),
                subtitle = stringResource(R.string.settings_backup_body),
                onClick = onBackup,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.Restore,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFFF9500),
                title = stringResource(R.string.settings_restore),
                subtitle = stringResource(R.string.settings_restore_short),
                onClick = onRestore,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.VisibilityOff,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                title = stringResource(R.string.settings_hidden_tracks),
                subtitle = stringResource(R.string.hidden_track_subtitle, hiddenCount),
                onClick = onHidden,
            )
        }

        GroupHeader(stringResource(R.string.settings_playback))
        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
            SettingRow(
                icon = Icons.Filled.Download,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                title = stringResource(R.string.settings_downloads),
                subtitle = stringResource(R.string.settings_downloads_body),
                onClick = onDownloads,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.History,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFFF9500),
                title = stringResource(R.string.home_history),
                subtitle = stringResource(R.string.home_history_body),
                onClick = onHistory,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.Bedtime,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF5856D6),
                title = stringResource(R.string.settings_sleep_default),
                subtitle = stringResource(R.string.sleep_minutes, page.sleepMinutes),
                onClick = onSleep,
            )
        }

        GroupHeader(stringResource(R.string.settings_cache))
        GlassGroup(Modifier.padding(horizontal = 16.dp)) {
            SettingRow(
                icon = Icons.Filled.Cloud,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                title = stringResource(R.string.settings_media_cache),
                subtitle = formatBytes(mediaCacheBytes),
                onClick = onMediaCache,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.CleaningServices,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF8E8E93),
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_cache_size, formatBytes(page.bytes)),
                onClick = onClearCache,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.BugReport,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF34C759),
                title = stringResource(R.string.settings_export_logs),
                subtitle = stringResource(R.string.settings_export_logs_body),
                onClick = onExportLogs,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.SystemUpdate,
                    iconColor = androidx.compose.ui.graphics.Color(0xFFFF9500),
                title = stringResource(R.string.settings_check_update),
                subtitle = stringResource(R.string.settings_check_update_body),
                onClick = onCheckUpdate,
            )
            GroupDivider()
            SettingRow(
                icon = Icons.Filled.Info,
                    iconColor = androidx.compose.ui.graphics.Color(0xFF007AFF),
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_version, stringResource(R.string.app_version)),
                onClick = onLicenses,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(accent.color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.slogan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThresholdRow(title: String, value: String, onClick: () -> Unit) {
    val palette = com.rabbeat.app.ui.theme.LocalRabBeatPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 58.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = palette.secondaryLabel)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = palette.tertiaryLabel,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ScanSwitch(checked: Boolean, onChecked: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onChecked,
        colors = SwitchDefaults.colors(checkedTrackColor = androidx.compose.ui.graphics.Color(0xFF34C759), checkedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedThumbColor = androidx.compose.ui.graphics.Color.White, uncheckedTrackColor = com.rabbeat.app.ui.theme.LocalRabBeatPalette.current.fill, uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun ScanNumberDialog(
    editing: ScanEdit,
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember(editing, initial) { mutableStateOf(initial.toString()) }
    val size = editing == ScanEdit.SIZE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (size) R.string.scan_edit_size else R.string.scan_edit_duration)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(6) },
                singleLine = true,
                suffix = { Text(if (size) "KB" else stringResource(R.string.scan_edit_seconds)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeteaseImportSheet(
    state: NeteaseImport,
    onStart: (String) -> Unit,
    onFinished: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = com.rabbeat.app.ui.theme.LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val context = LocalContext.current
    var link by remember { mutableStateOf("") }
    val running = state as? NeteaseImport.Running

    LaunchedEffect(state) {
        when (val current = state) {
            is NeteaseImport.Done -> onFinished(
                context.doneMessage(current.result),
            )
            NeteaseImport.BadLink -> onFinished(context.getString(R.string.netease_import_bad_link))
            NeteaseImport.Failed -> onFinished(context.getString(R.string.netease_import_failed))
            else -> {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (running == null) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.elevated,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    stringResource(R.string.settings_netease_import),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.label,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.netease_import_hint)) },
                singleLine = true,
                enabled = running == null,
            )
            Text(
                stringResource(R.string.netease_import_hint_body),
                style = MaterialTheme.typography.bodySmall,
                color = palette.secondaryLabel,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (running != null) {
                Text(
                    stringResource(R.string.netease_import_progress, running.done, running.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.secondaryLabel,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Button(
                onClick = { onStart(link.trim()) },
                enabled = running == null && link.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = accent.color),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.netease_import_action))
            }
        }
    }
}

private fun android.content.Context.doneMessage(result: NeteaseImportResult): String =
    getString(
        R.string.netease_import_done,
        result.playlistName,
        result.imported,
        result.matchedLocal,
        result.skipped,
    )

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
}
