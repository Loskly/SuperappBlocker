package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.ecosentinel.appblocker.security.PasswordSessionManager
import com.ecosentinel.appblocker.service.MonitorScreenGate
import com.ecosentinel.appblocker.survival.SurvivalManager
import com.ecosentinel.appblocker.ui.BlockOverlayManager
import com.ecosentinel.appblocker.ui.PasswordOverlayManager

class MonitorScreenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SCREEN_OFF -> onScreenOff(context)
            Intent.ACTION_USER_PRESENT -> onUserPresent(context)
            Intent.ACTION_SCREEN_ON -> onScreenOn(context)
        }
    }

    private fun onScreenOff(context: Context) {
        PasswordSessionManager.clearAll()
        BlockOverlayManager.hide(context)
        PasswordOverlayManager.hide(context)
        MonitorScreenGate.notifyScreenOff()
    }

    private fun onScreenOn(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isInteractive) {
            MonitorScreenGate.notifyScreenWake()
        }
        SurvivalManager.runCheck(context, SurvivalManager.REASON_SCREEN_ON)
    }

    private fun onUserPresent(context: Context) {
        MonitorScreenGate.notifyScreenWake()
        SurvivalManager.runCheck(context, SurvivalManager.REASON_USER_PRESENT)
    }
}
