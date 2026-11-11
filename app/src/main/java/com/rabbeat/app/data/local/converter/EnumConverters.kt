package com.rabbeat.app.data.local.converter

import androidx.room.TypeConverter
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.TrackOrigin

class EnumConverters {
    @TypeConverter fun fromSourceType(value: SourceType): String = value.name
    @TypeConverter fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter fun fromSourceStatus(value: SourceStatus): String = value.name
    @TypeConverter fun toSourceStatus(value: String): SourceStatus = SourceStatus.valueOf(value)

    @TypeConverter fun fromDownloadState(value: DownloadState): String = value.name
    @TypeConverter fun toDownloadState(value: String): DownloadState = DownloadState.valueOf(value)

    @TypeConverter fun fromTrackOrigin(value: TrackOrigin): String = value.name
    @TypeConverter fun toTrackOrigin(value: String): TrackOrigin = TrackOrigin.valueOf(value)
}
