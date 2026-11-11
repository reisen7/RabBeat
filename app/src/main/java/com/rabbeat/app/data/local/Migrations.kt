package com.rabbeat.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `play_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`trackId` TEXT NOT NULL, " +
                "`playedAt` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_playedAt` ON `play_history` (`playedAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_history_trackId` ON `play_history` (`trackId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `lyricUri` TEXT")
        db.execSQL("ALTER TABLE `tracks` ADD COLUMN `folder` TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `lyric_links` (" +
                "`trackId` TEXT NOT NULL, " +
                "`sourceTrackId` TEXT NOT NULL, " +
                "PRIMARY KEY(`trackId`))",
        )
    }
}
