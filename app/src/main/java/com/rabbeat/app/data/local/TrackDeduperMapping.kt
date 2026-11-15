package com.rabbeat.app.data.local

import com.rabbeat.app.data.local.entity.TrackEntity

internal fun TrackEntity.toCandidate(): DupCandidate = DupCandidate(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    fileSize = fileSize,
    mimeType = mimeType,
    folder = folder,
    hidden = hidden,
)
