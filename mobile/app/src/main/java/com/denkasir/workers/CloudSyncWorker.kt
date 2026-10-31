package com.denkasir.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.denkasir.data.repository.SyncRepository

class CloudSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val syncRepo = SyncRepository(applicationContext)
        val result = syncRepo.performFullSync()

        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
