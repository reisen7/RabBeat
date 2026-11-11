package com.rabbeat.app.data.mock

import androidx.room.withTransaction
import com.rabbeat.app.data.local.RabBeatDatabase
import com.rabbeat.app.domain.model.SourceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySeeder @Inject constructor(
    private val database: RabBeatDatabase,
) {
    suspend fun purgeDemo() = database.withTransaction {
        database.playlistDao().deleteDemoTracks()
        database.playHistoryDao().deleteDemo()
        database.lyricLinkDao().deleteDemo()
        database.trackDao().deleteDemo()
        database.albumDao().deleteDemo()
        database.artistDao().deleteDemo()
        database.sourceDao().delete("src_local")
    }

    suspend fun dropRemoteSources() = database.withTransaction {
        val remote = database.sourceDao().getAll()
            .filter { it.type != SourceType.LOCAL && it.type != SourceType.NETEASE }
        remote.forEach { source ->
            database.trackDao().deleteBySource(source.id)
            database.albumDao().deleteBySource(source.id)
            database.artistDao().deleteBySource(source.id)
            database.sourceDao().delete(source.id)
        }
    }
}
