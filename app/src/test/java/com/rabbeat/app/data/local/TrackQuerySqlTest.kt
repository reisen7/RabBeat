package com.rabbeat.app.data.local

import androidx.sqlite.db.SupportSQLiteQuery
import com.rabbeat.app.domain.model.TrackQuery
import com.rabbeat.app.domain.model.TrackSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackQuerySqlTest {
    private fun SupportSQLiteQuery.text() = sql

    private fun args(q: SupportSQLiteQuery): List<Any?> {
        val holder = mutableListOf<Any?>()
        q.bindTo(object : androidx.sqlite.db.SupportSQLiteProgram {
            override fun bindNull(index: Int) { holder.add(index - 1, null) }
            override fun bindLong(index: Int, value: Long) { holder.add(index - 1, value) }
            override fun bindDouble(index: Int, value: Double) { holder.add(index - 1, value) }
            override fun bindString(index: Int, value: String) { holder.add(index - 1, value) }
            override fun bindBlob(index: Int, value: ByteArray) { holder.add(index - 1, value) }
            override fun clearBindings() { holder.clear() }
            override fun close() {}
        })
        return holder
    }

    @Test
    fun defaultIsTitleSort() {
        val q = TrackQuerySql.select(TrackQuery())
        assertEquals("SELECT * FROM tracks ORDER BY title COLLATE NOCASE, id", q.text())
    }

    @Test
    fun sourceAndOfflineFilter() {
        val q = TrackQuerySql.select(TrackQuery(sourceId = "src_device", offlineOnly = true))
        assertTrue(q.text().contains("sourceId = ?"))
        assertTrue(q.text().contains("(origin != 'REMOTE' OR downloadState = 'DOWNLOADED')"))
        assertEquals(listOf("src_device"), args(q))
    }

    @Test
    fun folderFilterUsesCoalesce() {
        val q = TrackQuerySql.select(TrackQuery(folderPath = "本地"))
        assertTrue(q.text().contains("COALESCE(NULLIF(TRIM(folder), ''), '本地') = ?"))
        assertEquals(listOf("本地"), args(q))
    }

    @Test
    fun limitAndOffsetWrapInSubquery() {
        val q = TrackQuerySql.select(TrackQuery(sort = TrackSort.ADDED), limit = 500, offset = 7)
        assertEquals(
            "SELECT * FROM (SELECT * FROM tracks ORDER BY addedAt DESC, id) LIMIT ? OFFSET ?",
            q.text(),
        )
        assertEquals(listOf(500L, 7L), args(q))
    }

    @Test
    fun countWrapsCore() {
        val q = TrackQuerySql.count(TrackQuery())
        assertEquals(
            "SELECT COUNT(*) FROM (SELECT * FROM tracks ORDER BY title COLLATE NOCASE, id)",
            q.text(),
        )
    }

    @Test
    fun idsWrapsCore() {
        val q = TrackQuerySql.ids(TrackQuery(sourceId = "s1"))
        assertEquals(
            "SELECT id FROM (SELECT * FROM tracks WHERE sourceId = ? ORDER BY title COLLATE NOCASE, id)",
            q.text(),
        )
        assertEquals(listOf("s1"), args(q))
    }

    @Test
    fun sortVariants() {
        assertTrue(TrackQuerySql.select(TrackQuery(sort = TrackSort.DURATION)).text().contains("durationMs DESC"))
        assertTrue(TrackQuerySql.select(TrackQuery(sort = TrackSort.PLAY_COUNT)).text().contains("playCount DESC"))
    }
}
