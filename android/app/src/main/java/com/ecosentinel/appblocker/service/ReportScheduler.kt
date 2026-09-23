package com.ecosentinel.appblocker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ecosentinel.appblocker.data.repository.ReportSettings
import com.ecosentinel.appblocker.receiver.ReportReceiver
import com.ecosentinel.appblocker.ui.ReportOverlayManager
import java.util.concurrent.TimeUnit

object ReportScheduler {

    private const val TAG = "ReportScheduler"
    private const val REQUEST_CODE = 888_777

    fun scheduleNext(context: Context) {
        if (!ReportSettings.isEnabled(context)) {
            cancel(context)
            return
        }

        val type = ReportSettings.getScheduleType(context)
        val triggerAt = if (type == 0) {
            val intervalHours = ReportSettings.getIntervalHours(context)
            val intervalMs = TimeUnit.HOURS.toMillis(intervalHours.toLong())
            System.currentTimeMillis() + intervalMs
        } else {
            val times = ReportSettings.getExactTimes(context)
            if (times.isEmpty()) {
                cancel(context)
                return
            }
            calculateNextExactTime(times)
        }

        scheduleAt(context, triggerAt)
    }

    private fun calculateNextExactTime(times: Set<String>): Long {
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)

        var bestTime: Long = Long.MAX_VALUE

        for (timeStr in times) {
            val parts = timeStr.split(":")
            if (parts.size != 2) continue
            val h = parts[0].toIntOrNull() ?: continue
            val m = parts[1].toIntOrNull() ?: continue

            val candidate = java.util.Calendar.getInstance()
            candidate.set(java.util.Calendar.HOUR_OF_DAY, h)
            candidate.set(java.util.Calendar.MINUTE, m)
            candidate.set(java.util.Calendar.SECOND, 0)
            candidate.set(java.util.Calendar.MILLISECOND, 0)

            if (h < currentHour || (h == currentHour && m <= currentMinute)) {
                // Time has passed today, schedule for tomorrow
                candidate.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            val candTime = candidate.timeInMillis
            if (candTime < bestTime) {
                bestTime = candTime
            }
        }

        return if (bestTime == Long.MAX_VALUE) {
            System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24) // Fallback
        } else {
            bestTime
        }
    }

    fun scheduleSnooze(context: Context) {
        val snoozeMins = ReportSettings.getSnoozeDurationMins(context)
        val triggerAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(snoozeMins.toLong())
        scheduleAt(context, triggerAt)
    }

    private fun scheduleAt(context: Context, triggerAt: Long) {
        ReportSettings.setExpectedReportTime(context, triggerAt)
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pending = makePendingIntent(appContext)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending
                )
            }
            Log.d(TAG, "Report alarm scheduled at $triggerAt")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact alarm, trying inexact", e)
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun checkAndRestore(context: Context) {
        if (!ReportSettings.isEnabled(context)) {
            cancel(context)
            return
        }

        val expected = ReportSettings.getExpectedReportTime(context)
        val now = System.currentTimeMillis()

        if (expected in 1..now) {
            Log.i(TAG, "Missed report detected (expected=$expected, now=$now). Showing overlay.")
            ReportOverlayManager.show(context)
        } else if (expected > now) {
            Log.i(TAG, "Report alarm still in future, rescheduling for $expected")
            scheduleAt(context, expected)
        } else {
            Log.i(TAG, "No expected report time set, scheduling next.")
            scheduleNext(context)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(makePendingIntent(appContext))
        ReportSettings.setExpectedReportTime(context, 0L)
        Log.d(TAG, "Report alarm cancelled")
    }

    private fun makePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReportReceiver::class.java).apply {
            action = ReportReceiver.ACTION_REPORT_TRIGGER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
