package com.rabbeat.app.ui.screens.recent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.rabbeat.app.R
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.usecase.ToggleFavoriteUseCase
import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.playback.PlaybackUiState
import com.rabbeat.app.ui.components.AddToPlaylistDialog
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.components.TrackRowPlaceholder
import com.rabbeat.app.ui.components.groupedPageBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val RECENT_QUERY = TrackQuery(sort = TrackSort.ADDED)

@HiltViewModel
class RecentlyAddedViewModel @Inject constructor(
    private val music: MusicRepository,
    private val player: PlaybackController,
    private val favorites: ToggleFavoriteUseCase,
    private val downloads: DownloadRepository,
) : ViewModel() {
    val tracks = music.pageTracks(RECENT_QUERY).cachedIn(viewModelScope)
    val count = music.observeTrackCount(RECENT_QUERY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val playback: StateFlow<PlaybackUiState> = player.state

    fun play(index: Int) {
        viewModelScope.launch {
            player.playQueue(music.queueFrom(RECENT_QUERY, index), 0)
        }
    }
    fun playNext(track: com.rabbeat.app.domain.model.Track) = player.playNext(track)
    fun enqueue(track: com.rabbeat.app.domain.model.Track) = player.enqueue(track)
    fun favorite(id: String) { viewModelScope.launch { favorites(id) } }
    fun download(id: String) { viewModelScope.launch { downloads.enqueue(listOf(id)) } }
    fun hide(id: String) { viewModelScope.launch { music.hide(listOf(id)) } }
}

@Composable
fun RecentlyAddedScreen(onBack: () -> Unit, vm: RecentlyAddedViewModel = hiltViewModel()) {
    val items = vm.tracks.collectAsLazyPagingItems()
    val count by vm.count.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    var addTrackId by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(groupedPageBackground())) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = stringResource(R.string.home_recent),
                subtitle = stringResource(R.string.smart_count, count),
                leading = {
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        onClick = onBack,
                    )
                },
            )
            if (items.itemCount == 0) {
                EmptyState(stringResource(R.string.home_recent_empty), Modifier.fillMaxSize())
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
                            TrackRow(
                                track,
                                { vm.play(index) },
                                { vm.playNext(track) },
                                { vm.enqueue(track) },
                                { vm.favorite(track.id) },
                                { vm.download(track.id) },
                                onAddToPlaylist = { addTrackId = track.id },
                                onHide = { vm.hide(track.id) },
                                playing = playback.currentTrack?.id == track.id && playback.isPlaying,
                            )
                        }
                    }
                }
            }
        }
    }
    addTrackId?.let { trackId -> AddToPlaylistDialog(trackId) { addTrackId = null } }
}
