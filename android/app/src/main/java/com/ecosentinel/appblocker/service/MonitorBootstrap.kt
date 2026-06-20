package com.ecosentinel.appblocker.service

import android.content.Context
import com.ecosentinel.appblocker.util.PermissionHelper

object MonitorBootstrap {

    fun canRunMonitoring(context: Context): Boolean {
        return PermissionHelper.hasUsageAccess(context)
    }

    fun ensureMonitoring(context: Context) {
        if (!canRunMonitoring(context)) {
            return
        }
        UsageMonitorService.start(context)
    }
}
