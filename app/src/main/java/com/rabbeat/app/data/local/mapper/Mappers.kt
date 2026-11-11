package com.rabbeat.app.data.local.mapper

import com.rabbeat.app.data.local.entity.AlbumEntity
import com.rabbeat.app.data.local.entity.ArtistEntity
import com.rabbeat.app.data.local.entity.SourceEntity
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.domain.model.Album
import com.rabbeat.app.domain.model.Artist
import com.rabbeat.app.domain.model.MusicSource
import com.rabbeat.app.domain.model.SourceType
import com.rabbeat.app.domain.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class StoredSourceConfig(
    val baseUrl: String = "",
    val username: String = "",
    val root: String = "",
)

fun SourceEntity.toDomain(): MusicSource {
    val config = runCatching { json.decodeFromString<StoredSourceConfig>(configJson) }.getOrNull()
    return MusicSource(
        id = id,
        type = type,
        displayName = displayName,
        status = status,
        lastSyncAt = lastSyncAt,
        createdAt = createdAt,
        baseUrl = config?.baseUrl.orEmpty(),
        username = config?.username.orEmpty(),
    )
}

fun MusicSource.toEntity(): SourceEntity {
    val payload = StoredSourceConfig(baseUrl = baseUrl, username = username)
    return SourceEntity(
        id = id,
        type = type,
        displayName = displayName,
        configJson = json.encodeToString(payload),
        status = status,
        lastSyncAt = lastSyncAt,
        createdAt = createdAt,
    )
}

fun TrackEntity.toDomain(source: SourceEntity?): Track = Track(
    id = id,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    year = year,
    mimeType = mimeType,
    bitrate = bitrate,
    sampleRate = sampleRate,
    fileSize = fileSize,
    uri = uri,
    artworkUri = artworkUri,
    origin = origin,
    downloadState = downloadState,
    isFavorite = isFavorite,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    addedAt = addedAt,
    sourceType = source?.type ?: SourceType.LOCAL,
    sourceName = source?.displayName ?: "",
    folder = folder,
)

fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    sourceId = sourceId,
    title = title,
    artist = artist,
    year = year,
    artworkUri = artworkUri,
    trackCount = trackCount,
    addedAt = addedAt,
)

fun ArtistEntity.toDomain(): Artist = Artist(
    id = id,
    sourceId = sourceId,
    name = name,
    artworkUri = artworkUri,
    albumCount = albumCount,
    trackCount = trackCount,
)
