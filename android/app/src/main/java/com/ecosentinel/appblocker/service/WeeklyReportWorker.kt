package com.ecosentinel.appblocker.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.tracker.WeeklyReportSettings
import com.ecosentinel.appblocker.util.PermissionHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WeeklyReportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        WeeklyReportNotificationHelper.createChannel(context)

        if (!PermissionHelper.hasNotificationPermission(context)) {
            return Result.success()
        }

        val calendar = Calendar.getInstance()
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            return Result.success()
        }

        val tracker = UsageTracker(context)
        val weekStart = tracker.calendarWeekStartDateKey()
        if (WeeklyReportSettings.getLastSentWeekStart(context) == weekStart) {
            return Result.success()
        }

        val weekEnd = tracker.calendarWeekEndDateKey(weekStart)
        val effectiveEnd = tracker.effectiveWeekEndDateKey(weekStart, weekEnd)
        val report = tracker.loadWeeklyReport(weekStart, effectiveEnd)
        if (report.totalMillis <= 0L) {
            return Result.success()
        }

        WeeklyReportNotificationHelper.show(context, report)
        WeeklyReportSettings.setLastSentWeekStart(context, weekStart)
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "weekly_report_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
