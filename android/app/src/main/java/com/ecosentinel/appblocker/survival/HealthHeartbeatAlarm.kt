package com.ecosentinel.appblocker.survival

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.ecosentinel.appblocker.receiver.HeartbeatReceiver

object HealthHeartbeatAlarm {

    private const val TAG = "HealthHeartbeatAlarm"
    private const val REQUEST_CODE = 999_888
    const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms — falling back to inexact")
            scheduleInexact(appContext, alarmManager)
            return
        }

        val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
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
            Log.d(TAG, "Heartbeat alarm scheduled in ${HEARTBEAT_INTERVAL_MS / 1000}s")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact alarm, trying inexact", e)
            scheduleInexact(appContext, alarmManager)
        }
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(makePendingIntent(appContext))
        Log.d(TAG, "Heartbeat alarm cancelled")
    }

    private fun scheduleInexact(context: Context, alarmManager: AlarmManager) {
        val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            makePendingIntent(context)
        )
    }

    private fun makePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, HeartbeatReceiver::class.java).apply {
            action = HeartbeatReceiver.ACTION_HEARTBEAT
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
