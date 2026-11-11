package com.rabbeat.app.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort

internal object TrackQuerySql {
    private const val FOLDER_EXPR = "COALESCE(NULLIF(TRIM(folder), ''), '本地')"

    private fun where(query: TrackQuery): Pair<String, List<Any>> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()
        query.sourceId?.let {
            clauses += "sourceId = ?"
            args += it
        }
        if (query.offlineOnly) {
            clauses += "(origin != 'REMOTE' OR downloadState = 'DOWNLOADED')"
        }
        query.folderPath?.let {
            clauses += "$FOLDER_EXPR = ?"
            args += it
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

    fun select(query: TrackQuery, limit: Int? = null, offset: Int = 0): SupportSQLiteQuery {
        val (core, args) = core(query)
        return if (limit == null) {
            SimpleSQLiteQuery(core, args.toTypedArray())
        } else {
            SimpleSQLiteQuery(
                "SELECT * FROM ($core) LIMIT ? OFFSET ?",
                (args + limit + offset).toTypedArray(),
            )
        }
    }

    fun count(query: TrackQuery): SupportSQLiteQuery {
        val (core, args) = core(query)
        return SimpleSQLiteQuery("SELECT COUNT(*) FROM ($core)", args.toTypedArray())
    }

    fun ids(query: TrackQuery): SupportSQLiteQuery {
        val (core, args) = core(query)
        return SimpleSQLiteQuery("SELECT id FROM ($core)", args.toTypedArray())
    }
}
