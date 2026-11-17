package com.rabbeat.app.data.local

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Room's `androidx.room.withTransaction` asks for a SupportSQLite factory.
 * This database is opened with the bundled driver, which has none, so that
 * call crashes. A writer connection plus an immediate transaction is the
 * path that actually encloses the DAO calls.
 */
suspend fun <R> RoomDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor ->
        transactor.immediateTransaction { block() }
    }
