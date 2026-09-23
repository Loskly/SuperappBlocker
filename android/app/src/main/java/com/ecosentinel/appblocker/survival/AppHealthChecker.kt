package com.ecosentinel.appblocker.survival

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.ecosentinel.appblocker.admin.DeviceOwnerManager
import com.ecosentinel.appblocker.service.UsageMonitorService
import com.ecosentinel.appblocker.util.PermissionHelper

object AppHealthChecker {

    fun check(context: Context): AppHealthSnapshot {
        val appContext = context.applicationContext

        return AppHealthSnapshot(
            monitorRunning = UsageMonitorService.isRunning(appContext),
            usageAccessGranted = PermissionHelper.hasUsageAccess(appContext),
            overlayGranted = PermissionHelper.canDrawOverlays(appContext),
            accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(appContext),
            notificationGranted = PermissionHelper.hasNotificationPermission(appContext),
            batteryOptimizationIgnored = PermissionHelper.isIgnoringBatteryOptimizations(appContext),
            deviceOwner = runCatching { DeviceOwnerManager.isDeviceOwner(appContext) }.getOrDefault(false)
        )
    }
}
