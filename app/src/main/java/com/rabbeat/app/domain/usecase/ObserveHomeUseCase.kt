package com.rabbeat.app.domain.usecase

import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.SourceRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

data class HomeContent(
    val top: List<Track>,
    val recent: List<Track>,
    val sources: List<MusicSource>,
    val totalTracks: Int,
)

class ObserveHomeUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val sourceRepository: SourceRepository,
) {
    operator fun invoke(): Flow<HomeContent> = combine(
        musicRepository.observeRecentlyAdded(12),
        musicRepository.observeTracks(TrackQuery(), 8),
        sourceRepository.observeSources(),
        musicRepository.observeTrackCount(),
    ) { recent, top, sources, total ->
        HomeContent(
            top = top,
            recent = recent,
            sources = sources,
            totalTracks = total,
        )
    }.flowOn(Dispatchers.Default)
}

class ToggleFavoriteUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
) {
    suspend operator fun invoke(trackId: String) = musicRepository.toggleFavorite(trackId)
}
