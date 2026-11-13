package com.rabbeat.app.data.mock

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.domain.model.DownloadItem
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.TrackOrigin
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class MockDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    @ApplicationScope private val scope: CoroutineScope,
) : DownloadRepository {
    private val progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    override fun observeDownloads(): Flow<List<DownloadItem>> = combine(
        trackDao.observeAll(),
        progress,
        dismissed,
    ) { tracks, amounts, hidden ->
        tracks.filter { track ->
            track.id !in hidden && (track.downloadState != DownloadState.NONE || amounts.containsKey(track.id))
        }
            .sortedBy { stateOrder(it.downloadState) }
            .map { track ->
                DownloadItem(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    state = track.downloadState,
                    progress = when (track.downloadState) {
                        DownloadState.DOWNLOADED -> 1f
                        DownloadState.DOWNLOADING, DownloadState.QUEUED -> amounts[track.id] ?: 0f
                        else -> amounts[track.id] ?: 0f
                    },
                    sizeBytes = track.fileSize ?: 0L,
                )
            }
    }

    override suspend fun enqueue(trackIds: List<String>) {
        trackIds.forEach { id ->
            val current = trackDao.get(id) ?: return@forEach
            if (current.origin == TrackOrigin.LOCAL || current.downloadState == DownloadState.DOWNLOADED) return@forEach
            trackDao.updateDownloadState(id, DownloadState.QUEUED)
            scope.launch { runFakeDownload(id) }
        }
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<DownloadPlaceholderWorker>().build(),
        )
    }

    override suspend fun cancel(trackId: String) {
        progress.update { it - trackId }
        val current = trackDao.get(trackId) ?: return
        if (current.downloadState != DownloadState.DOWNLOADED) {
            trackDao.updateDownloadState(trackId, DownloadState.NONE)
        }
    }

    override suspend fun clearCompleted() {
        val finished = trackDao.observeAll().first()
            .filter { it.downloadState == DownloadState.DOWNLOADED || it.downloadState == DownloadState.FAILED }
        finished.filter { it.downloadState == DownloadState.FAILED }.forEach {
            trackDao.updateDownloadState(it.id, DownloadState.NONE)
        }
        dismissed.update { it + finished.map { track -> track.id } }
        progress.update { current -> current - finished.map { track -> track.id }.toSet() }
    }

    private suspend fun runFakeDownload(id: String) {
        trackDao.updateDownloadState(id, DownloadState.DOWNLOADING)
        repeat(20) { step ->
            progress.update { it + (id to (step + 1) / 20f) }
            delay(140)
        }
        trackDao.updateDownloadState(id, DownloadState.DOWNLOADED)
        trackDao.updateOrigin(id, TrackOrigin.CACHED)
        progress.update { it - id }
    }

    private fun stateOrder(state: DownloadState): Int = when (state) {
        DownloadState.DOWNLOADING -> 0
        DownloadState.QUEUED -> 1
        DownloadState.FAILED -> 2
        DownloadState.DOWNLOADED -> 3
        DownloadState.NONE -> 4
    }
}
