package com.ecosentinel.appblocker.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ecosentinel.appblocker.service.MonitorBootstrap

class AppBlockerDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        DeviceOwnerManager.applyPolicies(context)
        MonitorBootstrap.ensureMonitoring(context)
        Toast.makeText(context, "Device admin enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "Device admin disabled", Toast.LENGTH_SHORT).show()
    }
}
