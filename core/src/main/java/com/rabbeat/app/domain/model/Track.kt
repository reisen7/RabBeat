package com.rabbeat.app.domain.model

data class Track(
    val id: String,
    val sourceId: String,
    val remoteId: String?,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String,
    val albumId: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val fileSize: Long?,
    val uri: String,
    val artworkUri: String?,
    val origin: TrackOrigin,
    val downloadState: DownloadState,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val addedAt: Long,
    val sourceType: SourceType,
    val sourceName: String,
    val folder: String? = null,
) {
    val folderPath: String get() = folder?.takeIf { it.isNotBlank() } ?: "本地"
    val offlineAvailable: Boolean
        get() = origin != TrackOrigin.REMOTE || downloadState == DownloadState.DOWNLOADED
}
