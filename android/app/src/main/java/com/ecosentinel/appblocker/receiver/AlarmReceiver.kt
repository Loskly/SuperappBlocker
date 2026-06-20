package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecosentinel.appblocker.service.AlarmRingingService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM_FIRE) {
            return
        }
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        if (alarmId < 0L) {
            return
        }
        AlarmRingingService.start(context.applicationContext, alarmId)
    }

    companion object {
        const val ACTION_ALARM_FIRE = "com.ecosentinel.appblocker.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
}
