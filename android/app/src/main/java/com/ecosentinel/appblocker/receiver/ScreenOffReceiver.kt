package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecosentinel.appblocker.security.PasswordSessionManager

class ScreenOffReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) {
            return
        }
        PasswordSessionManager.clearAll()
    }
}
