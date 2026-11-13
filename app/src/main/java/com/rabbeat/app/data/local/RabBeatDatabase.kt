package com.rabbeat.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rabbeat.app.data.local.converter.EnumConverters
import com.rabbeat.app.data.local.dao.AlbumDao
import com.rabbeat.app.data.local.dao.ArtistDao
import com.rabbeat.app.data.local.dao.LyricLinkDao
import com.rabbeat.app.data.local.dao.PlayHistoryDao
import com.rabbeat.app.data.local.dao.PlaybackStateDao
import com.rabbeat.app.data.local.dao.PlaylistDao
import com.rabbeat.app.data.local.dao.SourceDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.LyricLinkEntity
import com.rabbeat.app.data.local.entity.PlayHistoryEntity
import com.rabbeat.app.data.local.entity.PlaybackStateEntity
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity

@Database(
    entities = [
        SourceEntity::class,
        TrackEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        PlaybackStateEntity::class,
        PlayHistoryEntity::class,
        LyricLinkEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
@TypeConverters(EnumConverters::class)
abstract class RabBeatDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun sourceDao(): SourceDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun lyricLinkDao(): LyricLinkDao
}
