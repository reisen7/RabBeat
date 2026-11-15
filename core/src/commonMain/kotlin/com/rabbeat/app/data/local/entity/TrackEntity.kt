package com.rabbeat.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.TrackOrigin

@Entity(
    tableName = "tracks",
    indices = [
        Index("albumId"),
        Index("artistId"),
        Index("sourceId"),
        Index(value = ["sourceId", "remoteId"], unique = true),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
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
    val lyricUri: String? = null,
    @ColumnInfo(defaultValue = "0") val lyricOffsetMs: Long = 0,
    val folder: String? = null,
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)
