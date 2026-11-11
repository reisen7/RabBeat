package com.rabbeat.app

import android.app.Application
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.mock.LibrarySeeder
import com.rabbeat.app.di.ApplicationScope
import com.rabbeat.app.playback.NeteaseCacheStatus
import com.rabbeat.app.playback.PlayerController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class RabBeatApp : Application() {
    @Inject lateinit var playerController: PlayerController
    @Inject lateinit var seeder: LibrarySeeder
    @Inject lateinit var likedPlaylist: LikedPlaylist
    @Inject lateinit var cacheStatus: NeteaseCacheStatus
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        playerController.warmUp()
        appScope.launch {
            seeder.purgeDemo()
            seeder.dropRemoteSources()
            likedPlaylist.ensure()
            cacheStatus.refresh()
        }
    }
}
