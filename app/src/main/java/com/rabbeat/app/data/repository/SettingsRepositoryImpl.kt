package com.rabbeat.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.domain.model.ScanSettings
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "rabbeat_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        prefs.string(Keys.THEME)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }
    }

    override val appearance: Flow<AppearanceSettings> = context.settingsDataStore.data.map { prefs ->
        AppearanceSettings(
            accent = prefs[Keys.ACCENT]?.let { runCatching { AccentTheme.valueOf(it) }.getOrNull() } ?: AccentTheme.ROSE,
            backgroundUri = prefs[Keys.BACKGROUND_URI]?.takeIf { it.isNotBlank() },
            backgroundEnabled = prefs[Keys.BACKGROUND_ENABLED] ?: true,
            backgroundBlur = (prefs[Keys.BACKGROUND_BLUR] ?: 22f).coerceIn(0f, 40f),
            backgroundDim = (prefs[Keys.BACKGROUND_DIM] ?: 0.55f).coerceIn(0f, 0.9f),
            cardOpacity = (prefs[Keys.CARD_OPACITY] ?: 0.3f).coerceIn(0f, 1f),
        )
    }

    override suspend fun setAccentTheme(accent: AccentTheme) {
        context.settingsDataStore.edit { it[Keys.ACCENT] = accent.name }
    }

    override suspend fun setBackgroundUri(uri: String?) {
        context.settingsDataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.BACKGROUND_URI) else prefs[Keys.BACKGROUND_URI] = uri
        }
    }

    override suspend fun setBackgroundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BACKGROUND_ENABLED] = enabled }
    }

    override suspend fun setBackgroundBlur(radiusDp: Float) {
        context.settingsDataStore.edit { it[Keys.BACKGROUND_BLUR] = radiusDp.coerceIn(0f, 40f) }
    }

    override suspend fun setBackgroundDim(dim: Float) {
        context.settingsDataStore.edit { it[Keys.BACKGROUND_DIM] = dim.coerceIn(0f, 0.9f) }
    }

    override suspend fun setCardOpacity(opacity: Float) {
        context.settingsDataStore.edit { it[Keys.CARD_OPACITY] = opacity.coerceIn(0f, 1f) }
    }

    override val onboardingDone: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.ONBOARDING] ?: false }

    override suspend fun setOnboardingDone(done: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDING] = done }
    }

    override val recentSearches: Flow<List<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.RECENT]?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    override suspend fun pushRecentSearch(query: String) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT]?.let { raw ->
                runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val next = listOf(cleaned) + current.filterNot { it.equals(cleaned, ignoreCase = true) }
            prefs[Keys.RECENT] = json.encodeToString(ListSerializer(String.serializer()), next.take(8))
        }
    }

    override suspend fun clearRecentSearches() {
        context.settingsDataStore.edit { it.remove(Keys.RECENT) }
    }

    override val sleepTimerMinutes: Flow<Int> = context.settingsDataStore.data.map { it[Keys.SLEEP_MINUTES] ?: 30 }
    override suspend fun setSleepTimerMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.SLEEP_MINUTES] = minutes }
    }

    override val sleepFade: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.SLEEP_FADE] ?: true }
    override suspend fun setSleepFade(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SLEEP_FADE] = enabled }
    }

    override val wifiOnlyDownloads: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.WIFI_ONLY] ?: false }
    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    override val autoOfflineFavorites: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.AUTO_OFFLINE] ?: false }
    override suspend fun setAutoOfflineFavorites(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_OFFLINE] = enabled }
    }

    override val scanSettings: Flow<ScanSettings> = context.settingsDataStore.data.map { it.toScanSettings() }

    override suspend fun updateScanSettings(transform: (ScanSettings) -> ScanSettings) {
        context.settingsDataStore.edit { prefs ->
            val next = transform(prefs.toScanSettings())
            prefs[Keys.SCAN_FILTER_NON_SONG] = next.filterNonSongs
            prefs[Keys.SCAN_SKIP_SMALL] = next.skipSmallFiles
            prefs[Keys.SCAN_MIN_KB] = next.minSizeKb.coerceIn(1, 102_400)
            prefs[Keys.SCAN_SKIP_SHORT] = next.skipShortTracks
            prefs[Keys.SCAN_MIN_SEC] = next.minDurationSec.coerceIn(1, 3_600)
        }
    }

    private fun Preferences.toScanSettings() = ScanSettings(
        filterNonSongs = this[Keys.SCAN_FILTER_NON_SONG] ?: true,
        skipSmallFiles = this[Keys.SCAN_SKIP_SMALL] ?: true,
        minSizeKb = (this[Keys.SCAN_MIN_KB] ?: 500).coerceIn(1, 102_400),
        skipShortTracks = this[Keys.SCAN_SKIP_SHORT] ?: true,
        minDurationSec = (this[Keys.SCAN_MIN_SEC] ?: 60).coerceIn(1, 3_600),
    )

    private fun Preferences.string(key: Preferences.Key<String>): String? = this[key]

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_theme")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val BACKGROUND_ENABLED = booleanPreferencesKey("background_enabled")
        val BACKGROUND_BLUR = floatPreferencesKey("background_blur")
        val BACKGROUND_DIM = floatPreferencesKey("background_dim")
        val CARD_OPACITY = floatPreferencesKey("card_opacity")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val RECENT = stringPreferencesKey("recent_searches")
        val SLEEP_MINUTES = intPreferencesKey("sleep_minutes")
        val SLEEP_FADE = booleanPreferencesKey("sleep_fade")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_OFFLINE = booleanPreferencesKey("auto_offline")
        val SCAN_FILTER_NON_SONG = booleanPreferencesKey("scan_filter_non_song")
        val SCAN_SKIP_SMALL = booleanPreferencesKey("scan_skip_small")
        val SCAN_MIN_KB = intPreferencesKey("scan_min_kb")
        val SCAN_SKIP_SHORT = booleanPreferencesKey("scan_skip_short")
        val SCAN_MIN_SEC = intPreferencesKey("scan_min_sec")
    }
}
