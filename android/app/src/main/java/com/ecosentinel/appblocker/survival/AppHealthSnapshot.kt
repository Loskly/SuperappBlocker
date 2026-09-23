package com.ecosentinel.appblocker.survival

import androidx.annotation.StringRes
import com.ecosentinel.appblocker.R

data class AppHealthSnapshot(
    val monitorRunning: Boolean,
    val usageAccessGranted: Boolean,
    val overlayGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val notificationGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val deviceOwner: Boolean
) {
    val protectionReady: Boolean
        get() = monitorRunning &&
            usageAccessGranted &&
            overlayGranted &&
            accessibilityEnabled &&
            notificationGranted &&
            batteryOptimizationIgnored

    val issues: List<AppHealthIssue>
        get() = buildList {
            if (!monitorRunning) add(AppHealthIssue.MONITOR_STOPPED)
            if (!usageAccessGranted) add(AppHealthIssue.USAGE_ACCESS_MISSING)
            if (!overlayGranted) add(AppHealthIssue.OVERLAY_MISSING)
            if (!accessibilityEnabled) add(AppHealthIssue.ACCESSIBILITY_MISSING)
            if (!notificationGranted) add(AppHealthIssue.NOTIFICATIONS_MISSING)
            if (!batteryOptimizationIgnored) add(AppHealthIssue.BATTERY_OPTIMIZATION_ACTIVE)
        }
}

enum class AppHealthIssue(@StringRes val labelRes: Int) {
    MONITOR_STOPPED(R.string.status_service),
    USAGE_ACCESS_MISSING(R.string.status_usage_access),
    OVERLAY_MISSING(R.string.status_overlay_access),
    ACCESSIBILITY_MISSING(R.string.stayfree_accessibility_title),
    NOTIFICATIONS_MISSING(R.string.status_notifications),
    BATTERY_OPTIMIZATION_ACTIVE(R.string.status_battery)
}
