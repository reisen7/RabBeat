package com.rabbeat.app.domain.model

data class Album(
    val id: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val year: Int?,
    val artworkUri: String?,
    val trackCount: Int,
    val addedAt: Long,
)

data class Artist(
    val id: String,
    val sourceId: String,
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val trackCount: Int,
)

data class MusicSource(
    val id: String,
    val type: SourceType,
    val displayName: String,
    val status: SourceStatus,
    val lastSyncAt: Long?,
    val createdAt: Long,
    val baseUrl: String = "",
    val username: String = "",
    val password: String? = null,
)

data class DownloadItem(
    val trackId: String,
    val title: String,
    val artist: String,
    val state: DownloadState,
    val progress: Float,
    val sizeBytes: Long,
)

data class SearchResult(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()

    companion object {
        val EMPTY = SearchResult(emptyList(), emptyList(), emptyList())
    }
}

data class SyncSummary(
    val trackCount: Int,
    val albumCount: Int,
    val missingMetadata: Int,
)

data class UserPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val updatedAt: Long,
    val isSystem: Boolean = false,
)

data class PlayRecord(
    val id: Long,
    val track: Track,
    val playedAt: Long,
)
