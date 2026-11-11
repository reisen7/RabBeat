package com.rabbeat.app.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    val cache: SimpleCache = SimpleCache(
        File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context),
    )

    private val upstream = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0")
        .setDefaultRequestProperties(mapOf("Referer" to "https://music.163.com/"))

    private val cached = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val dataSourceFactory: DefaultDataSource.Factory = DefaultDataSource.Factory(context, cached)

    val usedBytes: Long get() = cache.cacheSpace

    fun clear() {
        cache.keys.toList().forEach(cache::removeResource)
    }

    companion object {
        private const val MAX_BYTES = 2L shl 30
    }
}
