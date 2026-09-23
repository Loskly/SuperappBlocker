package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecosentinel.appblocker.survival.HealthHeartbeatAlarm
import com.ecosentinel.appblocker.survival.SurvivalManager

class HeartbeatReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_HEARTBEAT) {
            return
        }
        val pendingResult = goAsync()
        SurvivalManager.runCheck(context.applicationContext, SurvivalManager.REASON_HEARTBEAT) {
            HealthHeartbeatAlarm.schedule(context.applicationContext)
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_HEARTBEAT = "com.ecosentinel.appblocker.action.HEARTBEAT"
    }
}
