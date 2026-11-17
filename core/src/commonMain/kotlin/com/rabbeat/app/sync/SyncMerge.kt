package com.rabbeat.app.sync

import com.rabbeat.app.data.local.dao.PlaylistDao
import com.rabbeat.app.data.local.dao.TrackDao
import com.rabbeat.app.data.local.entity.PlaylistEntity
import com.rabbeat.app.data.local.entity.PlaylistTrackCrossRef

object SyncMerge {
    fun plan(
        desktopTracks: List<SyncTrack>,
        phoneTracks: List<SyncTrack>,
        desktopPlaylists: List<SyncPlaylist>,
        phonePlaylists: List<SyncPlaylist>,
        desktopLikes: List<String>,
        phoneLikes: List<String>,
        desktopOrderAt: Long = 0,
        phoneOrderAt: Long = 0,
        desktopLikesAt: Long = 0,
        phoneLikesAt: Long = 0,
    ): SyncPlan {
        val chosen = linkedMapOf<String, String>()
        desktopTracks.forEach { chosen[it.contentKey] = it.syncId }
        phoneTracks.forEach { track -> if (track.contentKey !in chosen) chosen[track.contentKey] = track.syncId }

        fun rewrite(tracks: List<SyncTrack>) = tracks.map { track ->
            chosen[track.contentKey]?.let { track.copy(syncId = it) } ?: track
        }

        val desktop = rewrite(desktopTracks)
        var phone = rewrite(phoneTracks)
        val idMap = (oldToNew(desktopTracks, desktop) + oldToNew(phoneTracks, phone)).toMutableMap()
        val known = indexTracks(desktop).toMutableMap()
        phone = phone.map { song ->
            if (lookupTrack(known, song) != null) return@map song
            val match = desktop.firstOrNull { sameSong(song, it) } ?: return@map song
            idMap[song.syncId] = match.syncId
            val alias = (song.aliasKeys + match.contentKey + match.aliasKeys)
                .filter { it.isNotBlank() && it != song.contentKey }
                .distinct()
            song.copy(syncId = match.syncId, aliasKeys = alias)
        }
        fun mapId(id: String): String {
            val once = idMap[id] ?: id
            return idMap[once] ?: once
        }

        val desktopLists = rewritePlaylists(desktopPlaylists, ::mapId)
        val phoneLists = rewritePlaylists(phonePlaylists, ::mapId)
        val playlists = withPlaylistOrder(
            mergePlaylists(desktopLists, phoneLists),
            desktopLists,
            phoneLists,
            desktopOrderAt,
            phoneOrderAt,
        )
        val (coverPull, coverPush) = playlistCovers(desktopLists, phoneLists)
        val likes = (if (phoneLikesAt > desktopLikesAt) phoneLikes else desktopLikes).map(::mapId).distinct()
        val phoneByKey = indexTracks(phone)
        val desktopByKey = indexTracks(desktop)
        val listed = (playlists.flatMap { it.trackSyncIds } + likes).toSet()
        // The song is already in the other library. Do not send the audio again.
        val pull = desktop.filter { it.syncId in listed && it.hasFile && lookupTrack(phoneByKey, it) == null }
        val push = phone.filter { it.syncId in listed && it.hasFile && lookupTrack(desktopByKey, it) == null }
        val adopt = phoneTracks.mapNotNull { track ->
            val syncId = chosen[track.contentKey] ?: return@mapNotNull null
            if (syncId != track.syncId) SyncAdopt(track.contentKey, syncId) else null
        }
        return SyncPlan(
            pull = pull,
            push = push,
            playlists = playlists,
            likes = likes,
            adopt = adopt,
            artPull = missing(desktop.filter { it.syncId in listed }, phoneByKey, ::hasCover),
            artPush = missing(phone.filter { it.syncId in listed }, desktopByKey, ::hasCover),
            lyricPull = missingLyrics(desktop.filter { it.syncId in listed }, phoneByKey),
            lyricPush = missingLyrics(phone.filter { it.syncId in listed }, desktopByKey),
            artLinks = artLinks(desktop.filter { it.syncId in listed }, phoneByKey),
            coverPull = coverPull,
            coverPush = coverPush,
            playlistOrderAt = maxOf(desktopOrderAt, phoneOrderAt),
            likesUpdatedAt = maxOf(desktopLikesAt, phoneLikesAt),
        )
    }

    /**
     * Sidebar order comes from the side that last rearranged, created, or deleted a playlist.
     * A tie keeps the computer. Playlists the winner does not have stay at the end, in their own order.
     */
    private fun withPlaylistOrder(
        merged: List<SyncPlaylist>,
        desktop: List<SyncPlaylist>,
        phone: List<SyncPlaylist>,
        desktopOrderAt: Long,
        phoneOrderAt: Long,
    ): List<SyncPlaylist> {
        val winner = if (desktopOrderAt >= phoneOrderAt) desktop else phone
        val winnerOrder = winner.associate { it.syncId to it.sortOrder }
        val maxWinner = winnerOrder.values.maxOrNull() ?: -1
        val extras = merged
            .filter { it.syncId !in winnerOrder }
            .sortedBy { it.sortOrder }
            .mapIndexed { index, playlist -> playlist.syncId to (maxWinner + 1 + index) }
            .toMap()
        return merged.map { playlist ->
            val sort = winnerOrder[playlist.syncId] ?: extras[playlist.syncId] ?: playlist.sortOrder
            if (sort == playlist.sortOrder) playlist else playlist.copy(sortOrder = sort)
        }
    }

    /**
     * A chosen cover file travels to the side that does not have one.
     * When both sides have a file, the newer playlist wins and a tie keeps the computer's.
     * A newer edit that cleared the cover does not receive the old file back.
     */
    private fun playlistCovers(desktop: List<SyncPlaylist>, phone: List<SyncPlaylist>): Pair<List<String>, List<String>> {
        val phoneById = phone.associateBy { it.syncId }
        val pull = mutableListOf<String>()
        val push = mutableListOf<String>()
        (desktop.map { it.syncId } + phone.map { it.syncId }).distinct().forEach { id ->
            val fromDesktop = desktop.find { it.syncId == id }
            val fromPhone = phoneById[id]
            val desktopFile = fromDesktop?.hasCover == true
            val phoneFile = fromPhone?.hasCover == true
            val phoneNewer = fromPhone != null && fromDesktop != null && fromPhone.updatedAt > fromDesktop.updatedAt
            when {
                phoneFile && desktopFile -> if (phoneNewer) push += id else pull += id
                phoneFile -> push += id
                desktopFile && !phoneNewer -> pull += id
            }
        }
        return pull to push
    }

    /** Same title, artist, and length. This is the same match the library already uses when importing. */
    private fun sameSong(a: SyncTrack, b: SyncTrack): Boolean {
        val title = norm(a.title)
        if (title.isEmpty() || title != norm(b.title)) return false
        if (norm(a.artist) != norm(b.artist)) return false
        return if (a.durationMs > 0L && b.durationMs > 0L) {
            kotlin.math.abs(a.durationMs - b.durationMs) <= 2_000L
        } else {
            norm(a.album) == norm(b.album)
        }
    }

    private fun norm(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun hasCover(track: SyncTrack): Boolean = track.hasArt || coverUrl(track.artUrl) != null

    /** Cover URLs the phone should keep for songs it already has, when it has no cover of its own. */
    private fun artLinks(source: List<SyncTrack>, other: Map<String, SyncTrack>): List<SyncArtLink> =
        source.mapNotNull { track ->
            val url = coverUrl(track.artUrl) ?: return@mapNotNull null
            val dest = lookupTrack(other, track) ?: return@mapNotNull null
            if (dest.hasArt || coverUrl(dest.artUrl) != null) return@mapNotNull null
            SyncArtLink(track.syncId, url)
        }

    private fun indexTracks(tracks: List<SyncTrack>): Map<String, SyncTrack> {
        val map = HashMap<String, SyncTrack>()
        tracks.forEach { track ->
            map.putIfAbsent(track.contentKey, track)
            track.aliasKeys.forEach { key -> if (key.isNotBlank()) map.putIfAbsent(key, track) }
        }
        return map
    }

    private fun lookupTrack(index: Map<String, SyncTrack>, track: SyncTrack): SyncTrack? {
        index[track.contentKey]?.let { return it }
        track.aliasKeys.forEach { key -> index[key]?.let { return it } }
        return null
    }

    private fun missing(source: List<SyncTrack>, other: Map<String, SyncTrack>, has: (SyncTrack) -> Boolean): List<String> =
        source.mapNotNull { track ->
            if (!has(track)) return@mapNotNull null
            if (lookupTrack(other, track)?.let(has) == true) return@mapNotNull null
            track.syncId
        }

    /** Lyric text when the other side has none, and a non-zero offset when the other side is still at zero. */
    private fun missingLyrics(source: List<SyncTrack>, other: Map<String, SyncTrack>): List<String> =
        source.mapNotNull { track ->
            val dest = lookupTrack(other, track)
            val needText = track.hasLyric && dest?.hasLyric != true
            val needOffset = track.lyricOffsetMs != 0L && (dest?.lyricOffsetMs ?: 0L) == 0L
            if (needText || (needOffset && (track.hasLyric || dest?.hasLyric == true))) track.syncId else null
        }

    private fun oldToNew(before: List<SyncTrack>, after: List<SyncTrack>): Map<String, String> {
        val rewritten = after.associateBy { it.contentKey }
        return buildMap {
            before.forEach { track ->
                val next = rewritten[track.contentKey]?.syncId ?: return@forEach
                if (next != track.syncId) put(track.syncId, next)
            }
        }
    }

    private fun rewritePlaylists(lists: List<SyncPlaylist>, mapId: (String) -> String): List<SyncPlaylist> =
        lists.map { playlist -> playlist.copy(trackSyncIds = playlist.trackSyncIds.map(mapId)) }

    private fun mergePlaylists(desktop: List<SyncPlaylist>, phone: List<SyncPlaylist>): List<SyncPlaylist> {
        val merged = linkedMapOf<String, SyncPlaylist>()
        (desktop + phone).forEach { playlist ->
            val current = merged[playlist.syncId]
            if (current == null || playlist.updatedAt > current.updatedAt) merged[playlist.syncId] = playlist
        }
        return merged.values.toList()
    }
}

/**
 * A saved cover file stays until its replacement bytes arrive.
 * A web address must not overwrite that file: it is the original playlist image.
 */
private fun mergedCover(current: String?, remote: SyncPlaylist): String? {
    val localFile = current != null && (current.startsWith("file:") || current.startsWith("content:"))
    return when {
        remote.hasCover || localFile -> current
        else -> remote.coverUrl ?: current
    }
}

suspend fun applySyncPlaylists(playlistDao: PlaylistDao, trackDao: TrackDao, playlists: List<SyncPlaylist>) {
    playlists.forEach { remote ->
        if (remote.syncId.isBlank()) return@forEach
        val existing = playlistDao.bySyncId(remote.syncId)
        if (existing?.isSystem == true) return@forEach
        val id = existing?.id ?: "syncpl_${remote.syncId}"
        playlistDao.upsert(
            PlaylistEntity(
                id = id,
                name = remote.name.ifBlank { "歌单" },
                isSystem = false,
                createdAt = existing?.createdAt ?: remote.updatedAt,
                updatedAt = remote.updatedAt,
                sortOrder = remote.sortOrder,
                coverUri = mergedCover(existing?.coverUri, remote),
                syncId = remote.syncId,
            ),
        )
        val previous = if (existing != null) playlistDao.refs(id) else emptyList()
        val unsynced = previous.mapNotNull { ref ->
            val track = trackDao.get(ref.trackId) ?: return@mapNotNull null
            if (track.syncId.isNullOrBlank()) track.id else null
        }
        val synced = remote.trackSyncIds.mapNotNull { trackDao.bySyncId(it)?.id }
        val ordered = (synced + unsynced).distinct()
        playlistDao.deleteTracks(id)
        if (ordered.isNotEmpty()) {
            playlistDao.upsertTracks(
                ordered.mapIndexed { index, trackId ->
                    PlaylistTrackCrossRef(id, trackId, index, remote.updatedAt)
                },
            )
        }
    }
}

suspend fun applySyncLikes(
    playlistDao: PlaylistDao,
    trackDao: TrackDao,
    syncIds: List<String>,
    updatedAt: Long,
) {
    val stamp = updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    if (playlistDao.get(LIKED_PLAYLIST_ID) == null) {
        playlistDao.upsert(
            PlaylistEntity(
                id = LIKED_PLAYLIST_ID,
                name = "我喜欢",
                isSystem = true,
                createdAt = stamp,
                updatedAt = stamp,
            ),
        )
    }
    val wanted = syncIds.distinct().mapNotNull { syncId ->
        val track = trackDao.bySyncId(syncId) ?: return@mapNotNull null
        if (!track.isFavorite) trackDao.setFavorite(track.id, true)
        track.id
    }
    val wantedSet = wanted.toSet()
    val previous = playlistDao.refs(LIKED_PLAYLIST_ID)
    val unsynced = previous.mapNotNull { ref ->
        val track = trackDao.get(ref.trackId) ?: return@mapNotNull null
        if (track.syncId.isNullOrBlank()) track.id else null
    }
    trackDao.favoriteIds().forEach { id ->
        if (id in wantedSet) return@forEach
        val track = trackDao.get(id) ?: return@forEach
        if (track.syncId.isNullOrBlank()) return@forEach
        trackDao.setFavorite(id, false)
    }
    val ordered = (wanted + unsynced).distinct()
    playlistDao.deleteTracks(LIKED_PLAYLIST_ID)
    if (ordered.isNotEmpty()) {
        playlistDao.upsertTracks(
            ordered.mapIndexed { index, trackId ->
                PlaylistTrackCrossRef(LIKED_PLAYLIST_ID, trackId, index, stamp)
            },
        )
    }
    playlistDao.touch(LIKED_PLAYLIST_ID, stamp)
}

private const val LIKED_PLAYLIST_ID = "system_liked"
