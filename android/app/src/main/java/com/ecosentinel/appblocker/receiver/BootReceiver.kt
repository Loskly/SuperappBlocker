package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecosentinel.appblocker.alarm.AlarmScheduler
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.service.MonitorWatchdogWorker
import com.ecosentinel.appblocker.service.WeeklyReportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val appContext = context.applicationContext
                MonitorWatchdogWorker.schedule(appContext)
                WeeklyReportWorker.schedule(appContext)
                MonitorBootstrap.ensureMonitoring(appContext)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.rescheduleAll(appContext)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
