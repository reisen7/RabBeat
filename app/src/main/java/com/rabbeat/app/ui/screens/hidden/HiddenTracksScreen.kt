package com.rabbeat.app.ui.screens.hidden

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rabbeat.app.R
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.ui.components.Artwork
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.TrackRowPlaceholder
import com.rabbeat.app.ui.components.groupedPageBackground
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HiddenTracksViewModel @Inject constructor(
    private val music: MusicRepository,
) : ViewModel() {
    val tracks = music.pageTracks(TrackQuery(hiddenOnly = true)).cachedIn(viewModelScope)
    val count = music.observeHiddenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun restore(id: String) { viewModelScope.launch { music.unhide(listOf(id)) } }
    fun restoreAll() {
        viewModelScope.launch { music.unhide(music.trackIds(TrackQuery(hiddenOnly = true))) }
    }
}

@Composable
fun HiddenTracksScreen(onBack: () -> Unit, vm: HiddenTracksViewModel = hiltViewModel()) {
    val items = vm.tracks.collectAsLazyPagingItems()
    val count by vm.count.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(groupedPageBackground())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.settings_hidden_tracks),
                subtitle = stringResource(R.string.hidden_track_subtitle, count),
                leading = {
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
                trailing = {
                    if (count > 0) {
                        GlassPill(
                            text = stringResource(R.string.hidden_restore_all),
                            icon = Icons.Filled.Restore,
                            onClick = vm::restoreAll,
                        )
                    }
                },
            )
            if (items.itemCount == 0) {
                EmptyState(stringResource(R.string.hidden_empty), Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 30.dp),
                ) {
                    items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                        val track = items[index]
                        if (track == null) {
                            TrackRowPlaceholder()
                        } else {
                            HiddenRow(track) { vm.restore(track.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenRow(track: Track, onRestore: () -> Unit) {
    val palette = LocalRabBeatPalette.current
    val accent = LocalAccentPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUri, track.title, Modifier.size(48.dp), 6.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.track_meta, track.artist, track.album),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onRestore) {
                Text(
                    stringResource(R.string.hidden_restore),
                    color = accent.color,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 80.dp)
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}
