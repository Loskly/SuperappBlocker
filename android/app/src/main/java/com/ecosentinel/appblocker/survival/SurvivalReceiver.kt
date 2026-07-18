package com.ecosentinel.appblocker.survival

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SurvivalReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reason = when (intent?.action) {
            Intent.ACTION_BATTERY_LOW -> SurvivalManager.REASON_BATTERY_LOW
            Intent.ACTION_BATTERY_OKAY -> SurvivalManager.REASON_BATTERY_OKAY
            Intent.ACTION_POWER_CONNECTED -> SurvivalManager.REASON_POWER_CONNECTED
            Intent.ACTION_POWER_DISCONNECTED -> SurvivalManager.REASON_POWER_DISCONNECTED
            Intent.ACTION_MY_PACKAGE_REPLACED -> SurvivalManager.REASON_PACKAGE_REPLACED
            else -> return
        }
        val pendingResult = goAsync()
        SurvivalManager.runCheck(context.applicationContext, reason) {
            pendingResult.finish()
        }
    }
}
