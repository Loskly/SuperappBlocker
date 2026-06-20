package com.ecosentinel.appblocker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.receiver.AlarmReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AlarmScheduler {

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val alarms = AppDatabase.getInstance(context).superAlarmDao().getAll()
        alarms.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, alarm: SuperAlarmEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, alarm.id)

        if (!alarm.enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAt = AlarmRepeatDays.computeNextTriggerMillis(alarm) ?: run {
            alarmManager.cancel(pendingIntent)
            return
        }

        val showIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            Intent(context, com.ecosentinel.appblocker.ui.SuperAlarmActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, alarmId))
    }

    private fun pendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRE
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
