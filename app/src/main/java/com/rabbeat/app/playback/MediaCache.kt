package com.rabbeat.app.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.rabbeat.app.domain.repository.SettingsRepository
import com.rabbeat.app.util.NetworkStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class MediaCache @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository,
    private val network: NetworkStatus,
) {
    @Volatile
    private var cellularAllowed = true

    init {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            settings.cellularOnlineCache.collect { cellularAllowed = it }
        }
    }

    val cache: SimpleCache = SimpleCache(
        File(context.cacheDir, "media"),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(context),
    )

    private val upstream = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Mozilla/5.0")
        .setDefaultRequestProperties(mapOf("Referer" to "https://music.163.com/"))

    private val guardedUpstream = DataSource.Factory {
        GuardedSource(upstream.createDataSource()) { !cellularAllowed && network.isMetered() }
    }

    private val cached = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(guardedUpstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    val dataSourceFactory: DefaultDataSource.Factory = DefaultDataSource.Factory(context, cached)

    val usedBytes: Long get() = cache.cacheSpace

    fun clear() {
        cache.keys.toList().forEach(cache::removeResource)
    }

    /** Refuses upstream reads when online caching is disabled on a metered network. */
    private class GuardedSource(
        private val delegate: DataSource,
        private val blocked: () -> Boolean,
    ) : DataSource {
        override fun open(dataSpec: DataSpec): Long {
            if (blocked()) throw IOException("online audio caching is disabled on mobile data")
            return delegate.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun getUri() = delegate.uri
        override fun getResponseHeaders() = delegate.responseHeaders
        override fun addTransferListener(transferListener: TransferListener) =
            delegate.addTransferListener(transferListener)

        override fun close() = delegate.close()
    }

    companion object {
        private const val MAX_BYTES = 2L shl 30
    }
}
