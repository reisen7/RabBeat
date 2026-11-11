package com.rabbeat.app.data.repository

import com.rabbeat.app.data.local.dao.AlbumDao
import com.rabbeat.app.data.local.dao.ArtistDao
import com.rabbeat.app.data.local.dao.SourceDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.mapper.toDomain
import com.rabbeat.app.data.local.mapper.toEntity
import com.rabbeat.app.data.remote.subsonic.SubsonicClient
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.SourceStatus
import com.rabbeat.app.domain.model.SyncSummary
import com.rabbeat.app.domain.repository.SourceRepository
import com.rabbeat.app.util.SecureConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SourceRepositoryImpl @Inject constructor(
    private val sourceDao: SourceDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val subsonicClient: SubsonicClient,
    private val secureConfigStore: SecureConfigStore,
) : SourceRepository {
    override fun observeSources(): Flow<List<MusicSource>> = sourceDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeSource(id: String): Flow<MusicSource?> = sourceDao.observeById(id).map { it?.toDomain() }

    override suspend fun addSource(source: MusicSource): Result<String> = runCatching {
        sourceDao.upsert(source.toEntity())
        source.password?.let { secureConfigStore.put("source_pwd_${source.id}", it) }
        source.id
    }

    override suspend fun removeSource(id: String) {
        trackDao.deleteBySource(id)
        albumDao.deleteBySource(id)
        artistDao.deleteBySource(id)
        sourceDao.delete(id)
        secureConfigStore.put("source_pwd_$id", "")
    }

    override suspend fun testConnection(id: String): Result<Unit> {
        val source = sourceDao.get(id)?.toDomain() ?: return Result.failure(IllegalArgumentException("missing source"))
        val password = secureConfigStore.get("source_pwd_$id").orEmpty()
        return testDraft(source.baseUrl, source.username, password)
    }

    override suspend fun testDraft(baseUrl: String, username: String, password: String): Result<Unit> =
        subsonicClient.ping(baseUrl, username, password)

    override suspend fun syncSource(id: String): Result<SyncSummary> = try {
        sourceDao.updateStatus(id, SourceStatus.CONNECTED, System.currentTimeMillis())
        Result.success(SyncSummary(trackDao.count(), albumDao.count(), 0))
    } catch (error: Exception) {
        sourceDao.updateStatus(id, SourceStatus.ERROR, null)
        Result.failure(error)
    }
}
