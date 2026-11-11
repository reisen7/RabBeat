package com.rabbeat.app.domain.repository

import com.rabbeat.app.domain.model.AccentTheme
import com.rabbeat.app.domain.model.Album
import com.rabbeat.app.domain.model.AppearanceSettings
import com.rabbeat.app.domain.model.Artist
import androidx.paging.PagingData
import com.rabbeat.app.domain.model.DownloadItem
import com.rabbeat.app.domain.model.FolderSummary
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.PlayRecord
import com.rabbeat.app.domain.model.ScanSettings
import com.rabbeat.app.domain.model.SearchResult
import com.rabbeat.app.domain.model.SyncSummary
import com.rabbeat.app.domain.model.ThemeMode
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.UserPlaylist
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun pageTracks(query: TrackQuery): Flow<PagingData<Track>>
    fun observeTracks(query: TrackQuery, limit: Int): Flow<List<Track>>
    fun observeTrackCount(query: TrackQuery = TrackQuery()): Flow<Int>
    suspend fun queueFrom(query: TrackQuery, offset: Int, limit: Int = 500): List<Track>
    suspend fun trackIds(query: TrackQuery): List<String>
    fun pageAlbums(): Flow<PagingData<Album>>
    fun pageArtists(): Flow<PagingData<Artist>>
    fun observeAlbumCount(): Flow<Int>
    fun observeArtistCount(): Flow<Int>
    fun observeFolders(): Flow<List<FolderSummary>>
    fun observeTracks(): Flow<List<Track>>
    fun observeAlbums(): Flow<List<Album>>
    fun observeArtists(): Flow<List<Artist>>
    fun observeRecentlyAdded(limit: Int = 20): Flow<List<Track>>
    fun observeOfflineTracks(): Flow<List<Track>>
    fun observeAlbum(albumId: String): Flow<Album?>
    fun observeAlbumTracks(albumId: String): Flow<List<Track>>
    fun observeArtist(artistId: String): Flow<Artist?>
    fun observeArtistTracks(artistId: String): Flow<List<Track>>
    fun search(query: String): Flow<SearchResult>
    suspend fun getTrack(id: String): Track?
    suspend fun toggleFavorite(id: String)
    suspend fun recordPlayback(id: String, positionMs: Long)
    suspend fun updateDuration(id: String, durationMs: Long)
    fun observePlayHistory(): Flow<List<PlayRecord>>
    suspend fun clearPlayHistory()
}

interface SourceRepository {
    fun observeSources(): Flow<List<MusicSource>>
    fun observeSource(id: String): Flow<MusicSource?>
    suspend fun addSource(source: MusicSource): Result<String>
    suspend fun removeSource(id: String)
    suspend fun testConnection(id: String): Result<Unit>
    suspend fun testDraft(baseUrl: String, username: String, password: String): Result<Unit>
    suspend fun syncSource(id: String): Result<SyncSummary>
}

interface DownloadRepository {
    fun observeDownloads(): Flow<List<DownloadItem>>
    suspend fun enqueue(trackIds: List<String>)
    suspend fun cancel(trackId: String)
    suspend fun clearCompleted()
}

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val appearance: Flow<AppearanceSettings>
    suspend fun setAccentTheme(accent: AccentTheme)
    suspend fun setBackgroundUri(uri: String?)
    suspend fun setBackgroundEnabled(enabled: Boolean)
    suspend fun setBackgroundBlur(radiusDp: Float)
    suspend fun setBackgroundDim(dim: Float)

    val onboardingDone: Flow<Boolean>
    suspend fun setOnboardingDone(done: Boolean)

    val recentSearches: Flow<List<String>>
    suspend fun pushRecentSearch(query: String)
    suspend fun clearRecentSearches()

    val sleepTimerMinutes: Flow<Int>
    suspend fun setSleepTimerMinutes(minutes: Int)

    val sleepFade: Flow<Boolean>
    suspend fun setSleepFade(enabled: Boolean)

    val wifiOnlyDownloads: Flow<Boolean>
    suspend fun setWifiOnlyDownloads(enabled: Boolean)

    val autoOfflineFavorites: Flow<Boolean>
    suspend fun setAutoOfflineFavorites(enabled: Boolean)

    val scanSettings: Flow<ScanSettings>
    suspend fun updateScanSettings(transform: (ScanSettings) -> ScanSettings)
}

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<UserPlaylist>>
    fun observeTracks(playlistId: String): Flow<List<Track>>
    suspend fun create(name: String): String
    suspend fun rename(id: String, name: String)
    suspend fun delete(id: String)
    suspend fun deleteMany(ids: List<String>)
    suspend fun reorder(orderedIds: List<String>)
    suspend fun addTracks(playlistId: String, trackIds: List<String>, atStart: Boolean = false)
    suspend fun removeTrack(playlistId: String, trackId: String)
    suspend fun removeTracks(playlistId: String, trackIds: List<String>)
    suspend fun reorderTracks(playlistId: String, orderedTrackIds: List<String>)
    suspend fun saveQueue(name: String, trackIds: List<String>)
}
