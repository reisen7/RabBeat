package com.rabbeat.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rabbeat.app.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayHistoryDao {
    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    @Insert
    suspend fun insertAll(entries: List<PlayHistoryEntity>)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<PlayHistoryEntity>

    @Query("DELETE FROM play_history")
    suspend fun clear()

    @Query("DELETE FROM play_history WHERE trackId LIKE 'trk\\_%' ESCAPE '\\'")
    suspend fun deleteDemo()

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistoryEntity>>

    @Query(
        "DELETE FROM play_history WHERE id NOT IN (" +
            "SELECT id FROM play_history ORDER BY playedAt DESC LIMIT :keep" +
            ")",
    )
    suspend fun trim(keep: Int)
}
