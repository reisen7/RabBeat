package com.rabbeat.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class NetworkStatus @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    val online: Flow<Boolean> = callbackFlow {
        if (manager == null) {
            trySend(true)
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(hasInternet())
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(hasInternet())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** True when the active network is metered (cellular). Defaults to false when unknown. */
    fun isMetered(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun hasInternet(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
