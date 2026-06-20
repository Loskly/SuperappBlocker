package com.ecosentinel.appblocker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = SyncApi(applicationContext).sync()
        return if (result.success) Result.success() else Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "appblocker_sync"

        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${UNIQUE_WORK_NAME}_once",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
