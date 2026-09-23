package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ecosentinel.appblocker.data.repository.ReportSettings
import com.ecosentinel.appblocker.ui.ReportOverlayManager

class ReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPORT_TRIGGER) {
            return
        }
        
        Log.i("ReportReceiver", "Report trigger received!")
        if (ReportSettings.isEnabled(context)) {
            ReportOverlayManager.show(context)
        }
    }

    companion object {
        const val ACTION_REPORT_TRIGGER = "com.ecosentinel.appblocker.ACTION_REPORT_TRIGGER"
    }
}
