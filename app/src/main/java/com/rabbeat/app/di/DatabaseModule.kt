package com.rabbeat.app.di

import android.content.Context
import androidx.room.Room
import com.rabbeat.app.data.local.MIGRATION_1_2
import com.rabbeat.app.data.local.MIGRATION_2_3
import com.rabbeat.app.data.local.MIGRATION_3_4
import com.rabbeat.app.data.local.MIGRATION_4_5
import com.rabbeat.app.data.local.MIGRATION_5_6
import com.rabbeat.app.data.local.MIGRATION_6_7
import com.rabbeat.app.data.local.MIGRATION_7_8
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.dao.LyricLinkDao
import com.rabbeat.app.data.local.dao.PlayHistoryDao
import com.rabbeat.app.data.local.dao.AlbumDao
import com.rabbeat.app.data.local.dao.ArtistDao
import com.rabbeat.app.data.local.dao.PlaybackStateDao
import com.rabbeat.app.data.local.dao.PlaylistDao
import com.rabbeat.app.data.local.dao.SourceDao
import com.rabbeat.app.data.local.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RabBeatDatabase =
        Room.databaseBuilder(context, RabBeatDatabase::class.java, "rabbeat.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun provideTrackDao(db: RabBeatDatabase): TrackDao = db.trackDao()
    @Provides fun provideAlbumDao(db: RabBeatDatabase): AlbumDao = db.albumDao()
    @Provides fun provideArtistDao(db: RabBeatDatabase): ArtistDao = db.artistDao()
    @Provides fun provideSourceDao(db: RabBeatDatabase): SourceDao = db.sourceDao()
    @Provides fun providePlaylistDao(db: RabBeatDatabase): PlaylistDao = db.playlistDao()
    @Provides fun providePlaybackStateDao(db: RabBeatDatabase): PlaybackStateDao = db.playbackStateDao()
    @Provides fun providePlayHistoryDao(db: RabBeatDatabase): PlayHistoryDao = db.playHistoryDao()
    @Provides fun provideLyricLinkDao(db: RabBeatDatabase): LyricLinkDao = db.lyricLinkDao()
}
