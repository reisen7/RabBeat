package com.rabbeat.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.FolderSummary
import com.rabbeat.app.domain.model.TrackOrigin
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE hidden = 0 ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId AND hidden = 0 ORDER BY discNumber, trackNumber")
    fun observeByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId AND hidden = 0 ORDER BY album, discNumber, trackNumber")
    fun observeByArtist(artistId: String): Flow<List<TrackEntity>>

    @Query(
        "SELECT * FROM tracks WHERE hidden = 0 AND (" +
            "title LIKE '%' || :q || '%' " +
            "OR artist LIKE '%' || :q || '%' OR album LIKE '%' || :q || '%') " +
            "ORDER BY title COLLATE NOCASE",
    )
    fun search(q: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE hidden = 0 ORDER BY addedAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE hidden = 0 AND (origin != 'REMOTE' OR downloadState = 'DOWNLOADED') ORDER BY title COLLATE NOCASE")
    fun observeOfflineAvailable(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE hidden = 1")
    fun observeHiddenCount(): Flow<Int>

    @Query("UPDATE tracks SET hidden = :hidden WHERE id IN (:ids)")
    suspend fun setHidden(ids: List<String>, hidden: Boolean)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE sourceId = :sourceId")
    suspend fun getBySource(sourceId: String): List<TrackEntity>

    @RawQuery(observedEntities = [TrackEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, TrackEntity>

    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<TrackEntity>

    @RawQuery(observedEntities = [TrackEntity::class])
    fun observeQuery(query: SupportSQLiteQuery): Flow<List<TrackEntity>>

    @RawQuery(observedEntities = [TrackEntity::class])
    fun observeCount(query: SupportSQLiteQuery): Flow<Int>

    @RawQuery
    suspend fun ids(query: SupportSQLiteQuery): List<String>

    @Query(
        "SELECT COALESCE(NULLIF(TRIM(folder), ''), '本地') AS path, COUNT(*) AS trackCount " +
            "FROM tracks WHERE hidden = 0 GROUP BY path ORDER BY path",
    )
    fun observeFolders(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM tracks WHERE sourceId != :sourceId")
    suspend fun getOtherSources(sourceId: String): List<TrackEntity>

    @Query(
        "UPDATE tracks SET isFavorite = isFavorite OR :isFavorite, " +
            "playCount = MAX(playCount, :playCount), " +
            "hidden = hidden OR :hidden, " +
            "lastPlayedAt = CASE WHEN :lastPlayedAt IS NULL THEN lastPlayedAt " +
            "WHEN lastPlayedAt IS NULL OR lastPlayedAt < :lastPlayedAt THEN :lastPlayedAt ELSE lastPlayedAt END, " +
            "addedAt = MIN(addedAt, :addedAt) WHERE id = :id",
    )
    suspend fun mergeStatsInto(id: String, isFavorite: Boolean, playCount: Int, lastPlayedAt: Long?, addedAt: Long, hidden: Boolean = false)

    @Query("UPDATE OR IGNORE playlist_tracks SET trackId = :to WHERE trackId = :from")
    suspend fun reassignPlaylistTracks(from: String, to: String)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :from")
    suspend fun removePlaylistTracks(from: String)

    @Query("UPDATE play_history SET trackId = :to WHERE trackId = :from")
    suspend fun reassignPlayHistory(from: String, to: String)

    @Query("UPDATE OR IGNORE lyric_links SET trackId = :to WHERE trackId = :from")
    suspend fun reassignLyricLinks(from: String, to: String)

    @Query("DELETE FROM lyric_links WHERE trackId = :from")
    suspend fun removeLyricLink(from: String)

    @Query("UPDATE playback_state SET trackId = :to WHERE trackId = :from")
    suspend fun reassignPlaybackState(from: String, to: String)

    @Query("DELETE FROM tracks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM tracks WHERE origin != 'REMOTE' OR downloadState = 'DOWNLOADED'")
    fun observeStoredBytes(): Flow<Long>

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET downloadState = :state WHERE id = :id")
    suspend fun updateDownloadState(id: String, state: DownloadState)

    @Query("UPDATE tracks SET origin = :origin WHERE id = :id")
    suspend fun updateOrigin(id: String, origin: TrackOrigin)

    @Query("UPDATE tracks SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE tracks SET isFavorite = :value WHERE id = :id")
    suspend fun setFavorite(id: String, value: Boolean)



    @Query("SELECT id FROM tracks WHERE isFavorite = 1")
    suspend fun favoriteIds(): List<String>

    @Query("UPDATE tracks SET isFavorite = 0")
    suspend fun clearFavorites()

    @Query("UPDATE tracks SET isFavorite = 1 WHERE id IN (:ids)")
    suspend fun markFavorites(ids: List<String>)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun markPlayed(id: String, playedAt: Long)

    @Query("UPDATE tracks SET durationMs = :durationMs WHERE id = :id")
    suspend fun updateDuration(id: String, durationMs: Long)

    @Query("UPDATE tracks SET lyricUri = :lyricUri WHERE id = :id")
    suspend fun updateLyricUri(id: String, lyricUri: String?)

    @Query("UPDATE tracks SET downloadState = :state WHERE id IN (:ids)")
    suspend fun updateDownloadStates(ids: List<String>, state: DownloadState)

    @Query("DELETE FROM tracks WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM tracks WHERE sourceId = 'src_local' OR id LIKE 'trk\\_%' ESCAPE '\\' OR uri LIKE 'android.resource://%'")
    suspend fun deleteDemo()
}
