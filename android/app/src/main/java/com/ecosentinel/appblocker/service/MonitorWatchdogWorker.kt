package com.ecosentinel.appblocker.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class MonitorWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        MonitorBootstrap.ensureMonitoring(applicationContext)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "appblocker_monitor_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
