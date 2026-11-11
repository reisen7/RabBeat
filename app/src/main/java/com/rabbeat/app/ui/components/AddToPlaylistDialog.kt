package com.rabbeat.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import com.rabbeat.app.domain.repository.PlaylistRepository
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
) : ViewModel() {
    val sheets = playlists.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(playlistId: String, trackId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            playlists.addTracks(playlistId, listOf(trackId))
            onDone()
        }
    }

    fun create(name: String, trackId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val id = playlists.create(name)
            playlists.addTracks(id, listOf(trackId))
            onDone()
        }
    }
}

private val LikedTileGradient = listOf(Color(0xFFFF5E8A), Color(0xFFD4237A))

private val TileGradients = listOf(
    listOf(Color(0xFFFF5E8A), Color(0xFFD4237A)),
    listOf(Color(0xFFFFB347), Color(0xFFFF7A45)),
    listOf(Color(0xFF5AC8FA), Color(0xFF007AFF)),
    listOf(Color(0xFF66D49B), Color(0xFF28A86B)),
    listOf(Color(0xFFC08CFF), Color(0xFF8E5CF7)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    trackId: String,
    vm: AddToPlaylistViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
) {
    val sheets by vm.sheets.collectAsStateWithLifecycle()
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val added: (String) -> () -> Unit = { target ->
        {
            Toast.makeText(context, context.getString(R.string.playlist_added, target), Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    val submit = {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) vm.create(trimmed, trackId, added(trimmed))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.elevated,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
                Text(
                    stringResource(if (creating) R.string.playlist_new else R.string.playlist_add),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.label,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = accent.color,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { if (creating) creating = false else onDismiss() }
                        .padding(vertical = 4.dp),
                )
                if (creating) {
                    val enabled = name.isNotBlank()
                    Text(
                        stringResource(R.string.action_ok),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) accent.color else palette.tertiaryLabel,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { submit() }
                            .padding(vertical = 4.dp),
                    )
                }
            }
            HorizontalDivider(color = palette.separator, thickness = 0.5.dp)

            if (creating) {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistTile(Icons.AutoMirrored.Filled.QueueMusic, TileGradients[sheets.count { !it.isSystem } % TileGradients.size])
                    Spacer(Modifier.width(14.dp))
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.playlist_name), color = palette.tertiaryLabel) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.label),
                        shape = RoundedCornerShape(10.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = palette.fill,
                            unfocusedContainerColor = palette.fill,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = accent.color,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                Spacer(Modifier.height(8.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    item {
                        SheetRow(
                            title = stringResource(R.string.playlist_new),
                            subtitle = null,
                            titleColor = accent.color,
                            leading = {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(accent.soft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Add, null, tint = accent.color, modifier = Modifier.size(28.dp))
                                }
                            },
                            showDivider = sheets.isNotEmpty(),
                            onClick = { creating = true },
                        )
                    }
                    val normalSheets = sheets.filter { !it.isSystem }
                    items(sheets, key = { it.id }) { sheet ->
                        SheetRow(
                            title = sheet.name,
                            subtitle = stringResource(R.string.playlist_count, sheet.trackCount),
                            titleColor = palette.label,
                            leading = {
                                if (sheet.isSystem) {
                                    PlaylistTile(Icons.Filled.Favorite, LikedTileGradient)
                                } else {
                                    val index = normalSheets.indexOf(sheet)
                                    PlaylistTile(Icons.AutoMirrored.Filled.QueueMusic, TileGradients[index % TileGradients.size])
                                }
                            },
                            showDivider = sheets.indexOf(sheet) != sheets.lastIndex,
                            onClick = { vm.add(sheet.id, trackId, added(sheet.name)) },
                        )
                    }
                    if (sheets.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.playlist_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.secondaryLabel,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PlaylistTile(icon: ImageVector, colors: List<Color>) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun SheetRow(
    title: String,
    subtitle: String?,
    titleColor: Color,
    leading: @Composable () -> Unit,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalRabBeatPalette.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.secondaryLabel, maxLines = 1)
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = palette.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 86.dp))
        }
    }
}
