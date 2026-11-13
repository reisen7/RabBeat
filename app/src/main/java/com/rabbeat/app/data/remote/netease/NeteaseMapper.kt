package com.rabbeat.app.data.remote.netease

import com.rabbeat.app.data.local.DupCandidate
import com.rabbeat.app.data.local.entity.TrackEntity
import com.rabbeat.app.domain.model.DownloadState
import com.rabbeat.app.domain.model.TrackOrigin

const val NETEASE_SOURCE_ID = "src_netease"

object NeteaseMapper {
    const val FOLDER = "网易云音乐"

    fun artistName(song: NeSong): String =
        song.ar.mapNotNull { it.name.trim().ifBlank { null } }.joinToString("/").ifBlank { "未知艺术家" }

    fun isPlayable(privilege: NePrivilege?): Boolean =
        privilege != null && privilege.pl > 0 && privilege.st >= 0

    fun trackId(song: NeSong): String = "ne_${song.id}"

    fun albumId(song: NeSong): String = "nealb_${artistName(song).lowercase()}_${song.al.name.lowercase()}"

    fun artistId(song: NeSong): String = "neart_${artistName(song).lowercase()}"

    fun streamUrl(songId: Long): String = "https://music.163.com/song/media/outer/url?id=$songId.mp3"

    fun artworkUrl(song: NeSong): String? = song.al.picUrl
        ?.replace("http://", "https://")
        ?.plus("?param=300y300")

    fun toTrackEntity(song: NeSong, now: Long): TrackEntity = TrackEntity(
        id = trackId(song),
        sourceId = NETEASE_SOURCE_ID,
        remoteId = song.id.toString(),
        title = song.name.trim().ifBlank { "未知歌曲" },
        artist = artistName(song),
        artistId = artistId(song),
        album = song.al.name.trim().ifBlank { "未知专辑" },
        albumId = albumId(song),
        durationMs = song.dt,
        trackNumber = null,
        discNumber = null,
        year = null,
        mimeType = "audio/mpeg",
        bitrate = 128000,
        sampleRate = null,
        fileSize = null,
        uri = streamUrl(song.id),
        artworkUri = artworkUrl(song),
        origin = TrackOrigin.REMOTE,
        downloadState = DownloadState.NONE,
        isFavorite = false,
        playCount = 0,
        lastPlayedAt = null,
        addedAt = now,
        folder = FOLDER,
    )

    internal fun toCandidate(song: NeSong): DupCandidate = DupCandidate(
        id = trackId(song),
        title = song.name,
        artist = artistName(song),
        album = song.al.name,
        durationMs = song.dt,
        fileSize = null,
        mimeType = "audio/mpeg",
        folder = FOLDER,
    )
}
