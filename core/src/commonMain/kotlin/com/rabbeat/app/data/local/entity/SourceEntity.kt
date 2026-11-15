package com.rabbeat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val type: SourceType,
    val displayName: String,
    val configJson: String,
    val status: SourceStatus,
    val lastSyncAt: Long?,
    val createdAt: Long,
)
