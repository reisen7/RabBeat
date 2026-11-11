package com.rabbeat.app.domain.model

data class TrackQuery(
    val sort: TrackSort = TrackSort.TITLE,
    val sourceId: String? = null,
    val offlineOnly: Boolean = false,
    val folderPath: String? = null,
)

data class FolderSummary(val path: String, val trackCount: Int)
