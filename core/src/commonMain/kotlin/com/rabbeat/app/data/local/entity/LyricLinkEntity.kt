package com.rabbeat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyric_links")
data class LyricLinkEntity(
    @PrimaryKey val trackId: String,
    val sourceTrackId: String,
)
