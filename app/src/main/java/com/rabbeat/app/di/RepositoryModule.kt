package com.rabbeat.app.di

import com.rabbeat.app.data.mock.MockDownloadRepository
import com.rabbeat.app.data.mock.MockMusicRepository
import com.rabbeat.app.data.remote.subsonic.SubsonicClient
import com.rabbeat.app.data.remote.subsonic.SubsonicClientStub
import com.rabbeat.app.data.repository.PlaylistRepositoryImpl
import com.rabbeat.app.data.repository.SettingsRepositoryImpl
import com.rabbeat.app.data.repository.SourceRepositoryImpl
import com.rabbeat.app.domain.repository.DownloadRepository
import com.rabbeat.app.domain.repository.MusicRepository
import com.rabbeat.app.domain.repository.PlaylistRepository
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.domain.repository.SourceRepository
import com.rabbeat.app.util.DataStoreSecureConfigStore
import com.rabbeat.app.util.SecureConfigStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusic(impl: MockMusicRepository): MusicRepository

    @Binds
    @Singleton
    abstract fun bindSources(impl: SourceRepositoryImpl): SourceRepository

    @Binds
    @Singleton
    abstract fun bindDownloads(impl: MockDownloadRepository): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindSettings(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPlaylists(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSecureConfig(impl: DataStoreSecureConfigStore): SecureConfigStore

    @Binds
    @Singleton
    abstract fun bindSubsonic(impl: SubsonicClientStub): SubsonicClient
}
