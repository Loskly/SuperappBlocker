package com.ecosentinel.appblocker.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.ecosentinel.appblocker.BuildConfig

object DeviceOwnerManager {

    private fun adminComponent(context: Context): ComponentName {
        return ComponentName(context, AppBlockerDeviceAdminReceiver::class.java)
    }

    fun devicePolicyManager(context: Context): DevicePolicyManager {
        return context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    fun isDeviceOwner(context: Context): Boolean {
        return devicePolicyManager(context).isDeviceOwnerApp(BuildConfig.APPLICATION_ID)
    }

    fun isAdminActive(context: Context): Boolean {
        return devicePolicyManager(context).isAdminActive(adminComponent(context))
    }

    fun applyPolicies(context: Context) {
        val dpm = devicePolicyManager(context)
        if (!isDeviceOwner(context)) {
            return
        }

        val admin = adminComponent(context)
        dpm.setUninstallBlocked(admin, BuildConfig.APPLICATION_ID, true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dpm.setApplicationHidden(admin, BuildConfig.APPLICATION_ID, false)
        }

        val restrictions = arrayOf(
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_FACTORY_RESET
        )
        for (restriction in restrictions) {
            try {
                dpm.addUserRestriction(admin, restriction)
            } catch (_: SecurityException) {
            }
        }

        try {
            dpm.setGlobalSetting(
                admin,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                (BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB).toString()
            )
        } catch (_: Exception) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                dpm.setPermissionPolicy(
                    admin,
                    DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT
                )
            } catch (_: Exception) {
            }
        }
    }
}
