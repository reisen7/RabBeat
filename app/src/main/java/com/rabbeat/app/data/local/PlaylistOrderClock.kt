package com.rabbeat.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** When the phone last rearranged, created, or deleted a user playlist. */
@Singleton
class PlaylistOrderClock @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun get(): Long = prefs().getLong(KEY, 0L)

    fun note() {
        prefs().edit().putLong(KEY, System.currentTimeMillis()).apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private companion object {
        const val PREFS = "device_sync"
        const val KEY = "playlist_order_at"
    }
}
