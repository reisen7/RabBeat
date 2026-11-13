package com.rabbeat.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.PlaybackStateEntity
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.domain.model.SourceStatus
import kotlinx.coroutines.flow.Flow

private const val ALBUM_VISIBLE_SQL =
    "SELECT a.id, a.sourceId, a.title, a.artist, a.year, a.artworkUri, a.addedAt, " +
        "(SELECT COUNT(*) FROM tracks t WHERE t.albumId = a.id AND t.hidden = 0) AS trackCount " +
        "FROM albums a " +
        "WHERE EXISTS (SELECT 1 FROM tracks t WHERE t.albumId = a.id AND t.hidden = 0)"

@Dao
interface AlbumDao {
    @Query("$ALBUM_VISIBLE_SQL ORDER BY a.title COLLATE NOCASE")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("$ALBUM_VISIBLE_SQL ORDER BY a.title COLLATE NOCASE")
    fun pagingSource(): PagingSource<Int, AlbumEntity>

    @Query("SELECT COUNT(*) FROM ($ALBUM_VISIBLE_SQL)")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeById(id: String): Flow<AlbumEntity?>

    @Query("$ALBUM_VISIBLE_SQL AND (a.title LIKE '%' || :q || '%' OR a.artist LIKE '%' || :q || '%') ORDER BY a.title COLLATE NOCASE")
    fun search(q: String): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("DELETE FROM albums WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM albums WHERE sourceId = 'src_local' OR id IN ('alb_night', 'alb_city', 'alb_freq')")
    suspend fun deleteDemo()
}

private const val ARTIST_VISIBLE_SQL =
    "SELECT a.id, a.sourceId, a.name, a.artworkUri, " +
        "(SELECT COUNT(DISTINCT t.albumId) FROM tracks t WHERE t.artistId = a.id AND t.hidden = 0) AS albumCount, " +
        "(SELECT COUNT(*) FROM tracks t WHERE t.artistId = a.id AND t.hidden = 0) AS trackCount " +
        "FROM artists a " +
        "WHERE EXISTS (SELECT 1 FROM tracks t WHERE t.artistId = a.id AND t.hidden = 0)"

@Dao
interface ArtistDao {
    @Query("$ARTIST_VISIBLE_SQL ORDER BY a.name COLLATE NOCASE")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("$ARTIST_VISIBLE_SQL ORDER BY a.name COLLATE NOCASE")
    fun pagingSource(): PagingSource<Int, ArtistEntity>

    @Query("SELECT COUNT(*) FROM ($ARTIST_VISIBLE_SQL)")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeById(id: String): Flow<ArtistEntity?>

    @Query("$ARTIST_VISIBLE_SQL AND a.name LIKE '%' || :q || '%' ORDER BY a.name COLLATE NOCASE")
    fun search(q: String): Flow<List<ArtistEntity>>

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)

    @Query("DELETE FROM artists WHERE sourceId = 'src_local' OR id IN ('art_lin', 'art_beidao')")
    suspend fun deleteDemo()
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY createdAt")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources")
    suspend fun getAll(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun get(id: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE id = :id")
    fun observeById(id: String): Flow<SourceEntity?>

    @Upsert
    suspend fun upsert(source: SourceEntity)

    @Upsert
    suspend fun upsertAll(sources: List<SourceEntity>)

    @Query("UPDATE sources SET status = :status, lastSyncAt = :syncedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: SourceStatus, syncedAt: Long?)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY isSystem DESC, sortOrder ASC, updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY isSystem DESC, sortOrder ASC, updatedAt DESC")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query(
        "SELECT pt.playlistId AS playlistId, COUNT(*) AS trackCount FROM playlist_tracks pt " +
            "JOIN tracks t ON t.id = pt.trackId AND t.hidden = 0 GROUP BY pt.playlistId",
    )
    fun observeVisibleCounts(): Flow<List<PlaylistVisibleCount>>

    data class PlaylistVisibleCount(val playlistId: String, val trackCount: Int)

    @Query(
        "SELECT p.id AS playlistId, (" +
            "SELECT t.artworkUri FROM playlist_tracks pt JOIN tracks t ON t.id = pt.trackId " +
            "WHERE pt.playlistId = p.id AND t.hidden = 0 AND t.artworkUri IS NOT NULL AND t.artworkUri != '' " +
            "ORDER BY pt.addedAt DESC, pt.position DESC LIMIT 1) AS artworkUri FROM playlists p",
    )
    fun observeLatestArtwork(): Flow<List<PlaylistLatestArtwork>>

    data class PlaylistLatestArtwork(val playlistId: String, val artworkUri: String?)

    @Query("UPDATE playlists SET coverUri = :coverUri WHERE id = :id")
    suspend fun setCover(id: String, coverUri: String?)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observeRefs(playlistId: String): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun refs(playlistId: String): List<PlaylistTrackCrossRef>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun trackCount(playlistId: String): Int

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<PlaylistTrackCrossRef>)

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("SELECT MIN(sortOrder) FROM playlists WHERE isSystem = 0")
    suspend fun minSortOrder(): Int?

    @Query("UPDATE playlists SET sortOrder = :order WHERE id = :id AND isSystem = 0")
    suspend fun setSortOrder(id: String, order: Int)

    @Query("UPDATE playlist_tracks SET position = position + :delta WHERE playlistId = :playlistId")
    suspend fun shift(playlistId: String, delta: Int)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setTrackPosition(playlistId: String, trackId: String, position: Int)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId IN (:trackIds)")
    suspend fun removeTracksIn(playlistId: String, trackIds: List<String>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteTracks(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE trackId LIKE 'trk\\_%' ESCAPE '\\'")
    suspend fun deleteDemoTracks()

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM playlist_tracks")
    suspend fun deleteAllTracks()

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
}

@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 0")
    suspend fun get(): PlaybackStateEntity?

    @Upsert
    suspend fun upsert(state: PlaybackStateEntity)
}
