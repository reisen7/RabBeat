package com.rabbeat.app.util

import android.os.SystemClock

/** Reports (done, total) while a library scan runs. `total = 0` means "not known yet". */
typealias ScanProgress = suspend (done: Int, total: Int) -> Unit

/** Throttles progress updates so Compose is not recomposed for every single file. */
class ScanReporter(private val emit: ScanProgress) {
    private var lastAt = 0L
    private var lastDone = -1

    suspend fun report(done: Int, total: Int, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && done == lastDone) return
        if (!force && now - lastAt < 120L) return
        lastAt = now
        lastDone = done
        emit(done, total)
    }
}
