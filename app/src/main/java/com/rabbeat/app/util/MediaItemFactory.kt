package com.rabbeat.app.util

import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.playback.NeteaseCacheStatus
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import javax.inject.Inject

class MediaItemFactory @Inject constructor() {
    fun create(track: Track, durationOverrideMs: Long? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.artworkUri?.let(android.net.Uri::parse))
            .build()
        val builder = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setMediaMetadata(metadata)
        if (track.sourceType == SourceType.NETEASE) {
            NeteaseCacheStatus.cacheKey(track.remoteId)?.let(builder::setCustomCacheKey)
        }
        val clipEnd = durationOverrideMs?.takeIf { it >= 500L }
        if (clipEnd != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0)
                    .setEndPositionMs(clipEnd)
                    .build(),
            )
        }
        return builder.build()
    }
}
