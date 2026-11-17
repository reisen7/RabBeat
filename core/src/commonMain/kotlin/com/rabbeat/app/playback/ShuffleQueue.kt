package com.rabbeat.app.playback

/**
 * One shuffle for the whole queue: the current track stays now-playing, and
 * everything else is placed after it in a single random order. Later next /
 * previous steps walk that list instead of rolling a new index each time.
 */
fun <T> shuffledAroundCurrent(tracks: List<T>, currentIndex: Int): Pair<List<T>, Int> {
    if (tracks.size <= 1) return tracks to currentIndex.coerceAtLeast(0)
    val at = currentIndex.coerceIn(tracks.indices)
    val rest = ArrayList<T>(tracks.size - 1)
    for (i in tracks.indices) {
        if (i != at) rest.add(tracks[i])
    }
    rest.shuffle()
    return listOf(tracks[at]) + rest to 0
}
