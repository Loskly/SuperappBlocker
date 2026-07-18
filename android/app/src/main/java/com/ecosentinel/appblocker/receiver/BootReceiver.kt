package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecosentinel.appblocker.survival.SurvivalManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val reason = when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> SurvivalManager.REASON_BOOT_COMPLETED
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> SurvivalManager.REASON_LOCKED_BOOT_COMPLETED
            else -> return
        }
        val pendingResult = goAsync()
        SurvivalManager.runCheck(context.applicationContext, reason) {
            pendingResult.finish()
        }
    }
}
