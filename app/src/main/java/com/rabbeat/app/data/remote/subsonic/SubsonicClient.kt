package com.rabbeat.app.data.remote.subsonic

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

interface SubsonicClient {
    suspend fun ping(baseUrl: String, username: String, password: String): Result<Unit>
}

/**
 * Prototype stub. Real OpenSubsonic calls stay behind this interface.
 * TODO: replace with OkHttp requests against /rest/ping.view.
 */
@Singleton
class SubsonicClientStub @Inject constructor() : SubsonicClient {
    @Suppress("unused")
    private val http: OkHttpClient = OkHttpClient()

    override suspend fun ping(baseUrl: String, username: String, password: String): Result<Unit> {
        delay(350)
        val host = baseUrl.trim()
        return if (host.isBlank() || host.contains("fail", ignoreCase = true)) {
            Result.failure(IllegalStateException("unreachable"))
        } else {
            Result.success(Unit)
        }
    }
}
