package com.ecosentinel.appblocker.util

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.modules.adult.BrowserUrlMonitorService
import com.ecosentinel.appblocker.service.MonitorBootstrap

enum class PermissionKind {
    USAGE_ACCESS,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    AUTOSTART,
    ACCESSIBILITY
}

data class PermissionCheckItem(
    val kind: PermissionKind,
    val label: String,
    val granted: Boolean?
)

object PermissionHelper {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        val powerManager = context.getSystemService<PowerManager>() ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val component = ComponentName(context, BrowserUrlMonitorService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        if (enabledServices.isEmpty()) {
            return false
        }
        return enabledServices.contains(component.flattenToString()) ||
            enabledServices.contains(component.flattenToShortString())
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    fun getMissingPermissions(context: Context): List<PermissionKind> {
        val missing = mutableListOf<PermissionKind>()
        if (!hasUsageAccess(context)) {
            missing += PermissionKind.USAGE_ACCESS
        }
        if (!canDrawOverlays(context)) {
            missing += PermissionKind.OVERLAY
        }
        if (!hasNotificationPermission(context)) {
            missing += PermissionKind.NOTIFICATIONS
        }
        if (!isIgnoringBatteryOptimizations(context)) {
            missing += PermissionKind.BATTERY_OPTIMIZATION
        }
        return missing
    }

    fun getPermissionCheckItems(context: Context): List<PermissionCheckItem> {
        return listOf(
            PermissionCheckItem(
                kind = PermissionKind.USAGE_ACCESS,
                label = context.getString(R.string.status_usage_access),
                granted = hasUsageAccess(context)
            ),
            PermissionCheckItem(
                kind = PermissionKind.OVERLAY,
                label = context.getString(R.string.status_overlay_access),
                granted = canDrawOverlays(context)
            ),
            PermissionCheckItem(
                kind = PermissionKind.NOTIFICATIONS,
                label = context.getString(R.string.status_notifications),
                granted = hasNotificationPermission(context)
            ),
            PermissionCheckItem(
                kind = PermissionKind.BATTERY_OPTIMIZATION,
                label = context.getString(R.string.status_battery),
                granted = isIgnoringBatteryOptimizations(context)
            ),
            PermissionCheckItem(
                kind = PermissionKind.AUTOSTART,
                label = context.getString(R.string.status_autostart),
                granted = null
            ),
            PermissionCheckItem(
                kind = PermissionKind.ACCESSIBILITY,
                label = context.getString(R.string.stayfree_accessibility_title),
                granted = isAccessibilityServiceEnabled(context)
            )
        )
    }

    fun openPermissionSettings(context: Context, kind: PermissionKind) {
        when (kind) {
            PermissionKind.USAGE_ACCESS -> openUsageAccessSettings(context)
            PermissionKind.OVERLAY -> openOverlaySettings(context)
            PermissionKind.NOTIFICATIONS -> openAppNotificationSettings(context)
            PermissionKind.BATTERY_OPTIMIZATION -> requestIgnoreBatteryOptimizations(context)
            PermissionKind.AUTOSTART -> openAutostartSettings(context)
            PermissionKind.ACCESSIBILITY -> openAccessibilitySettings(context)
        }
    }

    fun openAppNotificationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        openAppDetailsSettings(context)
    }

    fun countGrantedPermissions(context: Context): Pair<Int, Int> {
        val items = getPermissionCheckItems(context).filter { it.granted != null }
        val granted = items.count { it.granted == true }
        return granted to items.size
    }

    fun openUsageAccessSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        if (isIgnoringBatteryOptimizations(context)) {
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openBatterySettings(context)
        }
    }

    fun openBatterySettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openAutostartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val intents = buildList {
            when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    add(
                        intent(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                }
                manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                    add(
                        intent(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                    add(
                        intent(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.optimize.process.ProtectActivity"
                        )
                    )
                }
                manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> {
                    add(
                        intent(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                    add(
                        intent(
                            "com.oppo.safe",
                            "com.oppo.safe.permission.startup.StartupAppListActivity"
                        )
                    )
                }
                manufacturer.contains("vivo") -> {
                    add(
                        intent(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    )
                    add(
                        intent(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    )
                }
                manufacturer.contains("samsung") -> {
                    add(
                        intent(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    )
                }
            }
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }

        for (target in intents) {
            if (launchIfAvailable(context, target)) {
                return
            }
        }
        openAppDetailsSettings(context)
    }

    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    fun runNextSetupStep(context: Context): PermissionKind? {
        if (!hasUsageAccess(context)) {
            openUsageAccessSettings(context)
            return PermissionKind.USAGE_ACCESS
        }
        if (!canDrawOverlays(context)) {
            openOverlaySettings(context)
            return PermissionKind.OVERLAY
        }
        if (!hasNotificationPermission(context)) {
            return PermissionKind.NOTIFICATIONS
        }
        if (!isIgnoringBatteryOptimizations(context)) {
            requestIgnoreBatteryOptimizations(context)
            return PermissionKind.BATTERY_OPTIMIZATION
        }
        openAutostartSettings(context)
        return PermissionKind.AUTOSTART
    }

    fun ensureMonitoring(context: Context) {
        MonitorBootstrap.ensureMonitoring(context)
    }

    private fun intent(packageName: String, className: String): Intent {
        return Intent().apply {
            component = ComponentName(packageName, className)
        }
    }

    private fun launchIfAvailable(context: Context, intent: Intent): Boolean {
        val launchIntent = intent.apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return if (launchIntent.resolveActivity(context.packageManager) != null) {
            try {
                context.startActivity(launchIntent)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
    }
}
