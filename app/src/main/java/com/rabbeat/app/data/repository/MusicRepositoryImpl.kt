package com.rabbeat.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.rabbeat.app.data.local.LikedPlaylist
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.data.local.TrackQuerySql
import com.rabbeat.app.data.local.dao.AlbumDao
import com.rabbeat.app.data.local.dao.ArtistDao
import com.rabbeat.app.data.local.dao.PlayHistoryDao
import com.rabbeat.app.data.local.dao.SourceDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.PlayHistoryEntity
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.di.ApplicationScope
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val sourceDao: SourceDao,
    private val historyDao: PlayHistoryDao,
    private val likedPlaylist: LikedPlaylist,
    private val database: RabBeatDatabase,
    @ApplicationScope scope: CoroutineScope,
    private val logger: com.rabbeat.app.util.AppLogger,
) : MusicRepository {
    /**
     * Source metadata is cached here instead of being combined into the paging flow.
     * Re-emitting a [PagingData] from another flow makes the same PageFetcher get
     * collected twice, which crashes with "Attempt to collect twice from pageEventFlow".
     */
    private val sourceSnapshot = sourceDao.observeAll()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun pagingConfig() = PagingConfig(
        pageSize = 50,
        initialLoadSize = 100,
        prefetchDistance = 30,
        enablePlaceholders = true,
    )

    override fun pageTracks(query: TrackQuery): Flow<PagingData<Track>> =
        Pager(pagingConfig()) { trackDao.pagingSource(TrackQuerySql.select(query)) }.flow
            .map { data ->
                val byId = sourceSnapshot.value.associateBy { it.id }
                data.map { it.toDomain(byId[it.sourceId]) }
            }

    override fun observeTracks(query: TrackQuery, limit: Int): Flow<List<Track>> =
        combine(trackDao.observeQuery(TrackQuerySql.select(query, limit)), sourceDao.observeAll()) { tracks, sources ->
            val byId = sources.associateBy { it.id }
            tracks.map { it.toDomain(byId[it.sourceId]) }
        }

    override fun observeTrackCount(query: TrackQuery): Flow<Int> =
        trackDao.observeCount(TrackQuerySql.count(query))

    override suspend fun queueFrom(query: TrackQuery, offset: Int, limit: Int): List<Track> {
        val byId = sourceDao.getAll().associateBy { it.id }
        return trackDao.query(TrackQuerySql.select(query, limit, offset)).map { it.toDomain(byId[it.sourceId]) }
    }

    override suspend fun trackIds(query: TrackQuery): List<String> =
        trackDao.ids(TrackQuerySql.ids(query))

    override fun pageAlbums(): Flow<PagingData<Album>> =
        Pager(pagingConfig()) { albumDao.pagingSource() }.flow.map { data -> data.map { it.toDomain() } }

    override fun pageArtists(): Flow<PagingData<Artist>> =
        Pager(pagingConfig()) { artistDao.pagingSource() }.flow.map { data -> data.map { it.toDomain() } }

    override fun observeAlbumCount(): Flow<Int> = albumDao.observeCount()

    override fun observeArtistCount(): Flow<Int> = artistDao.observeCount()

    override fun observeFolders(): Flow<List<FolderSummary>> = trackDao.observeFolders()

    override fun observeTracks(): Flow<List<Track>> = combine(trackDao.observeAll(), sourceDao.observeAll()) { tracks, sources ->
        val byId = sources.associateBy { it.id }
        tracks.map { it.toDomain(byId[it.sourceId]) }
    }

    override fun observeAlbums(): Flow<List<Album>> = albumDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeArtists(): Flow<List<Artist>> = artistDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeRecentlyAdded(limit: Int): Flow<List<Track>> =
        combine(trackDao.observeRecentlyAdded(limit), sourceDao.observeAll()) { tracks, sources ->
            val byId = sources.associateBy { it.id }
            tracks.map { it.toDomain(byId[it.sourceId]) }
        }

    override fun observeOfflineTracks(): Flow<List<Track>> =
        combine(trackDao.observeOfflineAvailable(), sourceDao.observeAll()) { tracks, sources ->
            val byId = sources.associateBy { it.id }
            tracks.map { it.toDomain(byId[it.sourceId]) }
        }

    override fun observeAlbum(albumId: String): Flow<Album?> = albumDao.observeById(albumId).map { it?.toDomain() }

    override fun observeAlbumTracks(albumId: String): Flow<List<Track>> =
        combine(trackDao.observeByAlbum(albumId), sourceDao.observeAll()) { tracks, sources ->
            val byId = sources.associateBy { it.id }
            tracks.map { it.toDomain(byId[it.sourceId]) }
        }

    override fun observeArtist(artistId: String): Flow<Artist?> = artistDao.observeById(artistId).map { it?.toDomain() }

    override fun observeArtistTracks(artistId: String): Flow<List<Track>> =
        combine(trackDao.observeByArtist(artistId), sourceDao.observeAll()) { tracks, sources ->
            val byId = sources.associateBy { it.id }
            tracks.map { it.toDomain(byId[it.sourceId]) }
        }

    override fun search(query: String): Flow<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return kotlinx.coroutines.flow.flowOf(SearchResult.EMPTY)
        return combine(
            trackDao.search(q),
            albumDao.search(q),
            artistDao.search(q),
            sourceDao.observeAll(),
        ) { tracks, albums, artists, sources ->
            val byId = sources.associateBy { it.id }
            SearchResult(
                tracks = tracks.map { it.toDomain(byId[it.sourceId]) },
                albums = albums.map { it.toDomain() },
                artists = artists.map { it.toDomain() },
            )
        }
    }

    override suspend fun getTrack(id: String): Track? {
        val entity = trackDao.get(id) ?: return null
        return entity.toDomain(sourceDao.get(entity.sourceId))
    }

    override suspend fun getTracks(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        val entities = trackDao.getByIds(ids).associateBy { it.id }
        if (entities.isEmpty()) return emptyList()
        val sources = sourceDao.getAll().associateBy { it.id }
        return ids.mapNotNull { id -> entities[id]?.let { it.toDomain(sources[it.sourceId]) } }
    }

    override suspend fun toggleFavorite(id: String) = likedPlaylist.toggle(id)

    override suspend fun recordPlayback(id: String, positionMs: Long) {
        val playedAt = System.currentTimeMillis()
        trackDao.markPlayed(id, playedAt)
        historyDao.insert(PlayHistoryEntity(trackId = id, playedAt = playedAt))
        historyDao.trim(keep = 500)
    }

    override suspend fun updateDuration(id: String, durationMs: Long) {
        trackDao.updateDuration(id, durationMs)
    }

    override suspend fun updateDurations(durations: Map<String, Long>) {
        // One transaction -> a single invalidation, not one emission per row.
        database.withTransaction {
            durations.forEach { (id, ms) -> trackDao.updateDuration(id, ms) }
        }
    }

    override suspend fun hide(ids: List<String>) {
        ids.chunked(400).forEach { trackDao.setHidden(it, true) }
        logger.i("Library", "hid ${ids.size} tracks")
    }

    override suspend fun unhide(ids: List<String>) {
        ids.chunked(400).forEach { trackDao.setHidden(it, false) }
        logger.i("Library", "restored ${ids.size} hidden tracks")
    }

    override fun observeHiddenCount(): Flow<Int> = trackDao.observeHiddenCount()

    override fun observePlayHistory(): Flow<List<PlayRecord>> =
        combine(historyDao.observeRecent(200), observeTracks()) { rows, tracks ->
            val byId = tracks.associateBy { it.id }
            rows.mapNotNull { row -> byId[row.trackId]?.let { PlayRecord(row.id, it, row.playedAt) } }
        }

    override suspend fun clearPlayHistory() {
        historyDao.clear()
    }
}
