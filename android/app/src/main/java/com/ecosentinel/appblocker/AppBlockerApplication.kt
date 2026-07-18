package com.ecosentinel.appblocker

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.focus.FocusNotificationHelper
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.service.MonitorWatchdogWorker
import com.ecosentinel.appblocker.service.WeeklyReportNotificationHelper
import com.ecosentinel.appblocker.service.WeeklyReportWorker
import com.ecosentinel.appblocker.survival.SurvivalManager
import com.ecosentinel.appblocker.sync.SyncWorker
import com.ecosentinel.appblocker.util.WindowInsetsHelper
import java.util.concurrent.TimeUnit

class AppBlockerApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        WindowInsetsHelper.register(this)
        WeeklyReportNotificationHelper.createChannel(this)
        FocusNotificationHelper.createChannel(this)
        scheduleSyncWorker()
        MonitorWatchdogWorker.schedule(this)
        WeeklyReportWorker.schedule(this)
        MonitorBootstrap.ensureMonitoring(this)
        SurvivalManager.runCheck(this, SurvivalManager.REASON_APP_CREATE)
    }

    private fun scheduleSyncWorker() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
