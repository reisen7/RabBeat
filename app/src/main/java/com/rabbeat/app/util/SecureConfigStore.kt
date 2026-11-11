package com.rabbeat.app.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.secretDataStore by preferencesDataStore(name = "rabbeat_secrets")

interface SecureConfigStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
}

/**
 * Prototype store. Passwords are plain preferences.
 * TODO: swap this for EncryptedSharedPreferences before any real server password is kept.
 */
@Singleton
class DataStoreSecureConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SecureConfigStore {
    override suspend fun put(key: String, value: String) {
        val pref = stringPreferencesKey(key)
        context.secretDataStore.edit { prefs ->
            if (value.isEmpty()) prefs.remove(pref) else prefs[pref] = value
        }
    }

    override suspend fun get(key: String): String? {
        val pref = stringPreferencesKey(key)
        return context.secretDataStore.data.first()[pref]
    }
}
