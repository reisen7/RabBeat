package com.rabbeat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rabbeat.app.data.local.entity.LyricLinkEntity

@Dao
interface LyricLinkDao {
    @Query("SELECT * FROM lyric_links WHERE trackId = :trackId")
    suspend fun get(trackId: String): LyricLinkEntity?

    @Upsert
    suspend fun upsert(link: LyricLinkEntity)

    @Query("DELETE FROM lyric_links WHERE trackId = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM lyric_links WHERE trackId LIKE 'trk\\_%' ESCAPE '\\' OR sourceTrackId LIKE 'trk\\_%' ESCAPE '\\'")
    suspend fun deleteDemo()
}
