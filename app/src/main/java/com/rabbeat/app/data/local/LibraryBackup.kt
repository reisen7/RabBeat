package com.rabbeat.app.data.local

import android.content.Context
import android.net.Uri
import com.rabbeat.app.data.local.dao.PlayHistoryDao
import com.rabbeat.app.data.local.dao.PlaylistDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.PlayHistoryEntity
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RestoreResult(val playlists: Int, val missingTracks: Int)

@Singleton
class LibraryBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val historyDao: PlayHistoryDao,
    private val likedPlaylist: LikedPlaylist,
) {
    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val playlists = playlistDao.getAll().filter { !it.isSystem }
        val file = BackupFile(
            playlists = playlists.map { playlist ->
                BackupPlaylist(
                    name = playlist.name,
                    trackIds = playlistDao.refs(playlist.id).map { it.trackId },
                )
            },
            favoriteIds = trackDao.favoriteIds(),
            history = historyDao.recent(500).map { BackupHistory(it.trackId, it.playedAt) },
        )
        context.contentResolver.openOutputStream(target)?.use { output ->
            output.write(json.encodeToString(BackupFile.serializer(), file).toByteArray())
        } ?: error("无法写入备份文件")
    }

    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@withContext RestoreResult(0, 0)
        val file = json.decodeFromString(BackupFile.serializer(), text)
        val known = mutableSetOf<String>()
        (file.playlists.flatMap { it.trackIds } + file.favoriteIds + file.history.map { it.trackId })
            .distinct()
            .chunked(400)
            .forEach { chunk -> known += trackDao.getByIds(chunk).map { it.id } }
        playlistDao.deleteAllTracks()
        playlistDao.deleteAll()
        val now = System.currentTimeMillis()
        var missing = 0
        file.playlists.forEachIndexed { index, playlist ->
            val id = java.util.UUID.randomUUID().toString()
            playlistDao.upsert(
                PlaylistEntity(
                    id = id,
                    name = playlist.name.ifBlank { "歌单" },
                    isSystem = false,
                    createdAt = now,
                    updatedAt = now,
                    sortOrder = index,
                ),
            )
            val kept = playlist.trackIds.filter { idIn ->
                val present = idIn in known
                if (!present) missing++
                present
            }
            if (kept.isNotEmpty()) {
                playlistDao.upsertTracks(
                    kept.mapIndexed { index, trackId ->
                        PlaylistTrackCrossRef(id, trackId, index, now)
                    },
                )
            }
        }
        trackDao.clearFavorites()
        file.favoriteIds.filter { it in known }.distinct().chunked(400).forEach { trackDao.markFavorites(it) }
        likedPlaylist.ensure()
        val restoredHistory = file.history.filter { it.trackId in known }.map {
            PlayHistoryEntity(trackId = it.trackId, playedAt = it.playedAt)
        }
        historyDao.clear()
        if (restoredHistory.isNotEmpty()) historyDao.insertAll(restoredHistory)
        RestoreResult(file.playlists.size, missing)
    }

    @Serializable
    private data class BackupFile(
        val version: Int = 1,
        val playlists: List<BackupPlaylist> = emptyList(),
        val favoriteIds: List<String> = emptyList(),
        val history: List<BackupHistory> = emptyList(),
    )

    @Serializable
    private data class BackupPlaylist(val name: String, val trackIds: List<String>)

    @Serializable
    private data class BackupHistory(val trackId: String, val playedAt: Long)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
}
