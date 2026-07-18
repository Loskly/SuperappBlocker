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

    private const val BATTERY_LOW_PERCENT = 15

    fun check(context: Context): AppHealthSnapshot {
        val appContext = context.applicationContext
        val powerManager = appContext.getSystemService<PowerManager>()

        return AppHealthSnapshot(
            monitorRunning = UsageMonitorService.isRunning(appContext),
            usageAccessGranted = PermissionHelper.hasUsageAccess(appContext),
            overlayGranted = PermissionHelper.canDrawOverlays(appContext),
            accessibilityEnabled = PermissionHelper.isAccessibilityServiceEnabled(appContext),
            notificationGranted = PermissionHelper.hasNotificationPermission(appContext),
            batteryOptimizationIgnored = PermissionHelper.isIgnoringBatteryOptimizations(appContext),
            deviceOwner = runCatching { DeviceOwnerManager.isDeviceOwner(appContext) }.getOrDefault(false),
            powerSaveMode = powerManager?.isPowerSaveMode == true,
            batteryLow = isBatteryLow(appContext)
        )
    }

    private fun isBatteryLow(context: Context): Boolean {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return false
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return false
        }
        return level * 100 / scale <= BATTERY_LOW_PERCENT
    }
}
