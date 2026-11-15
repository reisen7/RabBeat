package com.rabbeat.app.data.local

import androidx.room.RoomRawQuery
import androidx.sqlite.SQLiteStatement
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort

object TrackQuerySql {
    private const val FOLDER_EXPR = "COALESCE(NULLIF(TRIM(folder), ''), '本地')"

    private fun where(query: TrackQuery): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        clauses += if (query.hiddenOnly) "hidden = 1" else "hidden = 0"
        query.sourceId?.let {
            clauses += "sourceId = ?"
            args += it
        }
        if (query.offlineOnly) {
            clauses += "(origin != 'REMOTE' OR downloadState = 'DOWNLOADED')"
        }
        query.folderPath?.let {
            if (query.folderRecursive) {
                clauses += "($FOLDER_EXPR = ? OR $FOLDER_EXPR LIKE ? || '/%')"
                args += it
                args += it
            } else {
                clauses += "$FOLDER_EXPR = ?"
                args += it
            }
        }
        return (if (clauses.isEmpty()) "" else " WHERE ${clauses.joinToString(" AND ")}") to args
    }

    private fun orderBy(query: TrackQuery): String = when (query.sort) {
        TrackSort.TITLE -> " ORDER BY title COLLATE NOCASE, id"
        TrackSort.ADDED -> " ORDER BY addedAt DESC, id"
        TrackSort.DURATION -> " ORDER BY durationMs DESC, id"
        TrackSort.PLAY_COUNT -> " ORDER BY playCount DESC, id"
    }

    private fun core(query: TrackQuery): Pair<String, List<Any>> {
        val (where, args) = where(query)
        val sql = StringBuilder("SELECT * FROM tracks").append(where).append(orderBy(query))
        return sql.toString() to args
    }

    private fun bind(statement: SQLiteStatement, args: List<Any>) {
        args.forEachIndexed { i, value ->
            val index = i + 1
            when (value) {
                is String -> statement.bindText(index, value)
                is Boolean -> statement.bindLong(index, if (value) 1 else 0)
                is Int -> statement.bindLong(index, value.toLong())
                is Long -> statement.bindLong(index, value)
                is Double -> statement.bindDouble(index, value)
                is Float -> statement.bindDouble(index, value.toDouble())
                else -> error("Unsupported query arg type: ${value::class}")
            }
        }
    }

    fun select(query: TrackQuery, limit: Int? = null, offset: Int = 0): RoomRawQuery {
        val (core, args) = core(query)
        return if (limit == null) {
            RoomRawQuery(core) { bind(it, args) }
        } else {
            RoomRawQuery("SELECT * FROM ($core) LIMIT ? OFFSET ?") { bind(it, args + limit + offset) }
        }
    }

    fun count(query: TrackQuery): RoomRawQuery {
        val (core, args) = core(query)
        return RoomRawQuery("SELECT COUNT(*) FROM ($core)") { bind(it, args) }
    }

    fun ids(query: TrackQuery): RoomRawQuery {
        val (core, args) = core(query)
        return RoomRawQuery("SELECT id FROM ($core)") { bind(it, args) }
    }
}
