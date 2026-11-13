package com.rabbeat.app

import android.app.Application
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.mock.LibrarySeeder
import com.rabbeat.app.di.ApplicationScope
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.playback.NeteaseCacheStatus
import com.rabbeat.app.util.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class RabBeatApp : Application() {
    @Inject lateinit var seeder: LibrarySeeder
    @Inject lateinit var likedPlaylist: LikedPlaylist
    @Inject lateinit var cacheStatus: NeteaseCacheStatus
    @Inject lateinit var logger: AppLogger
    @Inject lateinit var settings: SettingsRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        logger.i("App", "onCreate ${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})")
        // Player warm-up is deferred to after the first frames (see RabBeatApp);
        // building ExoPlayer + MediaSession here blocks the splash on main.
        appScope.launch {
            // Prime DataStore so the first UI read of theme/appearance is fast.
            settings.appearance.first()
            settings.themeMode.first()
            seeder.purgeDemo()
            seeder.dropRemoteSources()
            likedPlaylist.ensure()
            cacheStatus.refresh()
        }
    }
}
