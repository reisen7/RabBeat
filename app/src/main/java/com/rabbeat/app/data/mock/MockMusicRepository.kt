package com.rabbeat.app.data.mock

import androidx.paging.PagingData
import com.rabbeat.app.data.repository.MusicRepositoryImpl
import com.rabbeat.app.domain.model.Album
import com.rabbeat.app.domain.model.Artist
import com.rabbeat.app.domain.model.FolderSummary
import com.rabbeat.app.domain.model.PlayRecord
import com.rabbeat.app.domain.model.SearchResult
import com.rabbeat.app.domain.model.Track
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.repository.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

@Singleton
class MockMusicRepository @Inject constructor(
    private val room: MusicRepositoryImpl,
) : MusicRepository {
    private fun <T> paced(flow: Flow<T>): Flow<T> = flow.onStart { delay(180) }

    override fun pageTracks(query: TrackQuery): Flow<PagingData<Track>> = room.pageTracks(query)
    override fun observeTracks(query: TrackQuery, limit: Int): Flow<List<Track>> = room.observeTracks(query, limit)
    override fun observeTrackCount(query: TrackQuery): Flow<Int> = room.observeTrackCount(query)
    override suspend fun queueFrom(query: TrackQuery, offset: Int, limit: Int): List<Track> = room.queueFrom(query, offset, limit)
    override suspend fun trackIds(query: TrackQuery): List<String> = room.trackIds(query)
    override fun pageAlbums(): Flow<PagingData<Album>> = room.pageAlbums()
    override fun pageArtists(): Flow<PagingData<Artist>> = room.pageArtists()
    override fun observeAlbumCount(): Flow<Int> = room.observeAlbumCount()
    override fun observeArtistCount(): Flow<Int> = room.observeArtistCount()
    override fun observeFolders(): Flow<List<FolderSummary>> = room.observeFolders()
    override fun observeTracks(): Flow<List<Track>> = paced(room.observeTracks())
    override fun observeAlbums(): Flow<List<Album>> = paced(room.observeAlbums())
    override fun observeArtists(): Flow<List<Artist>> = paced(room.observeArtists())
    override fun observeRecentlyAdded(limit: Int): Flow<List<Track>> = paced(room.observeRecentlyAdded(limit))
    override fun observeOfflineTracks(): Flow<List<Track>> = paced(room.observeOfflineTracks())
    override fun observeAlbum(albumId: String): Flow<Album?> = paced(room.observeAlbum(albumId))
    override fun observeAlbumTracks(albumId: String): Flow<List<Track>> = paced(room.observeAlbumTracks(albumId))
    override fun observeArtist(artistId: String): Flow<Artist?> = paced(room.observeArtist(artistId))
    override fun observeArtistTracks(artistId: String): Flow<List<Track>> = paced(room.observeArtistTracks(artistId))
    override fun search(query: String): Flow<SearchResult> = paced(room.search(query))
    override suspend fun getTrack(id: String): Track? = room.getTrack(id)
    override suspend fun toggleFavorite(id: String) = room.toggleFavorite(id)
    override suspend fun recordPlayback(id: String, positionMs: Long) = room.recordPlayback(id, positionMs)
    override suspend fun updateDuration(id: String, durationMs: Long) = room.updateDuration(id, durationMs)
    override suspend fun hide(ids: List<String>) = room.hide(ids)
    override suspend fun unhide(ids: List<String>) = room.unhide(ids)
    override fun observeHiddenCount(): Flow<Int> = room.observeHiddenCount()
    override fun observePlayHistory(): Flow<List<PlayRecord>> = paced(room.observePlayHistory())
    override suspend fun clearPlayHistory() = room.clearPlayHistory()
}
