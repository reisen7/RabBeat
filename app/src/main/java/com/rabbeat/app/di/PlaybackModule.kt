package com.rabbeat.app.di

import com.rabbeat.app.playback.PlaybackController
import com.rabbeat.app.playback.PlayerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(impl: PlayerController): PlaybackController
}
