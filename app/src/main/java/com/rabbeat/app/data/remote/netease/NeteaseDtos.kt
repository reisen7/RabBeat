package com.rabbeat.app.data.remote.netease

import kotlinx.serialization.Serializable

@Serializable
data class NePlaylistResponse(
    val playlist: NePlaylist? = null,
)

@Serializable
data class NePlaylist(
    val name: String = "",
    val coverImgUrl: String? = null,
    val trackIds: List<NeTrackId> = emptyList(),
)

@Serializable
data class NeTrackId(val id: Long)

@Serializable
data class NeSongDetailResponse(
    val songs: List<NeSong> = emptyList(),
    val privileges: List<NePrivilege> = emptyList(),
)

@Serializable
data class NeSong(
    val id: Long,
    val name: String = "",
    val ar: List<NeArtist> = emptyList(),
    val al: NeAlbum = NeAlbum(),
    val dt: Long = 0,
)

@Serializable
data class NeArtist(val name: String = "")

@Serializable
data class NeAlbum(
    val name: String = "",
    val picUrl: String? = null,
)

@Serializable
data class NePrivilege(
    val id: Long,
    val pl: Int = 0,
    val st: Int = 0,
)

@Serializable
data class NeLyricResponse(
    val lrc: NeLrc? = null,
    val tlyric: NeLrc? = null,
)

@Serializable
data class NeLrc(val lyric: String? = null)
