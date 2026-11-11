package com.rabbeat.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.rabbeat.app.domain.model.PlayRecord
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.playback.PlayerController
import com.rabbeat.app.ui.components.EmptyState
import com.rabbeat.app.ui.components.FailedState
import com.rabbeat.app.ui.components.GlassPill
import com.rabbeat.app.ui.components.HeaderIconButton
import com.rabbeat.app.ui.components.ScreenHeader
import com.rabbeat.app.ui.components.LoadingState
import com.rabbeat.app.ui.components.PillTabRow
import com.rabbeat.app.ui.components.ScreenState
import com.rabbeat.app.ui.components.TrackRow
import com.rabbeat.app.ui.theme.LocalAccentPalette
import com.rabbeat.app.ui.theme.LocalRabBeatPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryPage(
    val recent: List<PlayRecord>,
    val ranked: List<Track>,
    val totalPlays: Int,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val music: MusicRepository,
    private val player: PlayerController,
) : ViewModel() {
    val page = combine(music.observePlayHistory(), music.observeTracks()) { recent, tracks ->
        val ranked = tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
        ScreenState.Ready(
            HistoryPage(
                recent = recent,
                ranked = ranked,
                totalPlays = ranked.sumOf { it.playCount },
            ),
        ) as ScreenState<HistoryPage>
    }.catch { emit(ScreenState.Failed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun play(tracks: List<Track>, index: Int) = player.playQueue(tracks, index)
    fun clearHistory() { viewModelScope.launch { music.clearPlayHistory() } }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, vm: HistoryViewModel = hiltViewModel()) {
    val state by vm.page.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cleared = stringResource(R.string.history_cleared)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.home_history),
            subtitle = stringResource(R.string.history_subtitle),
            leading = {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    onClick = onBack,
                )
            },
            trailing = {
                GlassPill(
                    text = stringResource(R.string.history_clear),
                    icon = Icons.Filled.DeleteSweep,
                    onClick = {
                        vm.clearHistory()
                        scope.launch { snack.showSnackbar(cleared) }
                    },
                )
            },
        )
        PillTabRow(
            tabs = listOf(stringResource(R.string.history_recent), stringResource(R.string.history_counts)),
            selectedIndex = tab,
            onSelected = { tab = it },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val current = state) {
                ScreenState.Loading -> LoadingState(Modifier.fillMaxSize())
                ScreenState.Failed -> FailedState(Modifier.fillMaxSize()) { }
                ScreenState.Empty -> EmptyState(stringResource(R.string.history_empty), Modifier.fillMaxSize())
                is ScreenState.Ready -> {
                    val page = current.value
                    if (tab == 0) {
                        RecentList(page.recent) { index -> vm.play(page.recent.map { it.track }, index) }
                    } else {
                        CountList(page) { index -> vm.play(page.ranked, index) }
                    }
                }
            }
        }
        SnackbarHost(snack)
    }
}

@Composable
private fun RecentList(records: List<PlayRecord>, onPlay: (Int) -> Unit) {
    if (records.isEmpty()) {
        EmptyState(stringResource(R.string.history_empty), Modifier.fillMaxSize())
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
            Column {
                Text(
                    playedLabel(record.playedAt),
                    modifier = Modifier.padding(start = 22.dp, top = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TrackRow(record.track, { onPlay(index) }, { onPlay(index) }, { onPlay(index) }, {}, {})
            }
        }
    }
}

@Composable
private fun CountList(page: HistoryPage, onPlay: (Int) -> Unit) {
    if (page.ranked.isEmpty()) {
        EmptyState(stringResource(R.string.history_stats_empty), Modifier.fillMaxSize())
        return
    }
    val max = page.ranked.maxOf { it.playCount }.coerceAtLeast(1)
    val accent = LocalAccentPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                stringResource(R.string.history_summary, page.totalPlays, page.ranked.size),
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        itemsIndexed(page.ranked, key = { _, track -> track.id }) { index, track ->
            Column {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(index) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (index < 3) accent.color else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                track.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            stringResource(R.string.history_count, track.playCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                    LinearProgressIndicator(
                        progress = { track.playCount.toFloat() / max },
                        modifier = Modifier.fillMaxWidth(),
                        color = accent.color,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp)
                        .height(0.5.dp)
                        .background(LocalRabBeatPalette.current.separator),
                )
            }
        }
    }
}

@Composable
private fun playedLabel(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.now().atZone(zone).toLocalDate()
    val clock = "%02d:%02d".format(time.hour, time.minute)
    val date = time.toLocalDate()
    return when {
        date == today -> stringResource(R.string.history_today, clock)
        date == today.minusDays(1) -> stringResource(R.string.history_yesterday, clock)
        date.year == today.year -> stringResource(R.string.history_date, date.monthValue, date.dayOfMonth, clock)
        else -> stringResource(R.string.history_date_year, date.year, date.monthValue, date.dayOfMonth, clock)
    }
}
