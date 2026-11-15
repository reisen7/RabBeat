package com.rabbeat.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

private fun SQLiteConnection.execSQL(sql: String) {
    prepare(sql).use { it.step() }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `play_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trackId` TEXT NOT NULL, " +
                "`playedAt` INTEGER NOT NULL)",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` ON `play_history` (`playedAt`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_trackId` ON `play_history` (`trackId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `tracks` ADD COLUMN `lyricUri` TEXT")
        connection.execSQL("ALTER TABLE `tracks` ADD COLUMN `folder` TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            "UPDATE playlists SET sortOrder = (" +
                "SELECT COUNT(*) FROM playlists p2 " +
                "WHERE p2.updatedAt > playlists.updatedAt " +
                "OR (p2.updatedAt = playlists.updatedAt AND p2.id < playlists.id))",
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `tracks` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `playlists` ADD COLUMN `coverUri` TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `tracks` ADD COLUMN `lyricOffsetMs` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `lyric_links` (" +
                "`trackId` TEXT NOT NULL, " +
                "`sourceTrackId` TEXT NOT NULL, " +
                "PRIMARY KEY(`trackId`))",
        )
    }
}
