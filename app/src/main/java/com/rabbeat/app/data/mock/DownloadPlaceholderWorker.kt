package com.rabbeat.app.data.mock

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * Placeholder for the real download engine. Progress is simulated in [MockDownloadRepository].
 */
class DownloadPlaceholderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        delay(300)
        return Result.success()
    }
}
