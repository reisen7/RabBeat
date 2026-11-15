package com.rabbeat.app.playback

import com.rabbeat.app.domain.model.Track

object QueueManager {
    fun move(items: List<Track>, from: Int, to: Int): List<Track> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        val next = items.toMutableList()
        val item = next.removeAt(from)
        next.add(to, item)
        return next
    }

    fun remove(items: List<Track>, index: Int): List<Track> {
        if (index !in items.indices) return items
        return items.toMutableList().also { it.removeAt(index) }
    }

    fun shiftedIndex(current: Int, from: Int, to: Int): Int = when {
        current == from -> to
        from < current && to >= current -> current - 1
        from > current && to <= current -> current + 1
        else -> current
    }
}
