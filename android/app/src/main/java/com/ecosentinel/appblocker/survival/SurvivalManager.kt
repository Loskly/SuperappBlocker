package com.ecosentinel.appblocker.survival

import android.content.Context
import android.util.Log
import com.ecosentinel.appblocker.alarm.AlarmScheduler
import com.ecosentinel.appblocker.focus.FocusExpireScheduler
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.focus.FocusNotificationHelper
import com.ecosentinel.appblocker.service.AlarmNotificationHelper
import com.ecosentinel.appblocker.service.LimitWarningNotificationHelper
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.service.MonitorNotificationHelper
import com.ecosentinel.appblocker.service.MonitorWatchdogWorker
import com.ecosentinel.appblocker.service.WeeklyReportNotificationHelper
import com.ecosentinel.appblocker.service.WeeklyReportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SurvivalManager {

    const val REASON_APP_CREATE = "app_create"
    const val REASON_BOOT_COMPLETED = "boot_completed"
    const val REASON_LOCKED_BOOT_COMPLETED = "locked_boot_completed"
    const val REASON_PACKAGE_REPLACED = "package_replaced"
    const val REASON_SCREEN_ON = "screen_on"
    const val REASON_USER_PRESENT = "user_present"
    const val REASON_BATTERY_LOW = "battery_low"
    const val REASON_BATTERY_OKAY = "battery_okay"
    const val REASON_POWER_CONNECTED = "power_connected"
    const val REASON_POWER_DISCONNECTED = "power_disconnected"
    const val REASON_WATCHDOG = "watchdog"
    const val REASON_PERMISSIONS_SCREEN = "permissions_screen"
    const val REASON_MANUAL = "manual"

    private const val TAG = "SurvivalManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun runCheck(context: Context, reason: String) {
        runCheck(context, reason, onComplete = null)
    }

    fun runCheck(context: Context, reason: String, onComplete: (() -> Unit)?) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                runCheckNow(appContext, reason)
            } finally {
                onComplete?.invoke()
            }
        }
    }

    suspend fun runCheckNow(context: Context, reason: String): AppHealthSnapshot = withContext(Dispatchers.Default) {
        val appContext = context.applicationContext
        val beforeRestore = AppHealthChecker.check(appContext)

        restoreNotificationChannels(appContext)
        restoreWorkers(appContext)
        restoreMonitoring(appContext, beforeRestore)
        restoreFocus(appContext)
        if (shouldRescheduleAlarms(reason)) {
            restoreSuperAlarms(appContext)
        }

        val afterRestore = AppHealthChecker.check(appContext)
        if (afterRestore.protectionReady) {
            SurvivalNotificationHelper.cancelWarning(appContext)
        } else {
            SurvivalNotificationHelper.showWarning(appContext, afterRestore)
        }
        afterRestore
    }

    private fun restoreNotificationChannels(context: Context) {
        safe("notification channels") {
            SurvivalNotificationHelper.createChannel(context)
            MonitorNotificationHelper.createChannel(context)
            LimitWarningNotificationHelper.createChannel(context)
            FocusNotificationHelper.createChannel(context)
            WeeklyReportNotificationHelper.createChannel(context)
            AlarmNotificationHelper.createChannel(context)
        }
    }

    private fun restoreWorkers(context: Context) {
        safe("workers") {
            MonitorWatchdogWorker.schedule(context)
            WeeklyReportWorker.schedule(context)
        }
    }

    private fun restoreMonitoring(context: Context, snapshot: AppHealthSnapshot) {
        if (!snapshot.usageAccessGranted) {
            return
        }
        safe("monitoring") {
            MonitorBootstrap.ensureMonitoring(context)
        }
    }

    private suspend fun restoreFocus(context: Context) {
        runCatching {
            val focusManager = FocusManager(context)
            val session = focusManager.getActiveSession()
            FocusNotificationHelper.sync(context, session)
            if (session != null) {
                FocusExpireScheduler.schedule(context, session.expiresAtMillis)
            } else {
                FocusExpireScheduler.cancel(context)
            }
        }.onFailure {
            Log.w(TAG, "Failed to restore focus session", it)
        }
    }

    private suspend fun restoreSuperAlarms(context: Context) {
        runCatching {
            AlarmScheduler.rescheduleAll(context)
        }.onFailure {
            Log.w(TAG, "Failed to reschedule super alarms", it)
        }
    }

    private fun shouldRescheduleAlarms(reason: String): Boolean {
        return reason in setOf(
            REASON_APP_CREATE,
            REASON_BOOT_COMPLETED,
            REASON_PACKAGE_REPLACED,
            REASON_BATTERY_OKAY,
            REASON_POWER_CONNECTED,
            REASON_POWER_DISCONNECTED,
            REASON_MANUAL
        )
    }

    private inline fun safe(label: String, block: () -> Unit) {
        runCatching(block).onFailure {
            Log.w(TAG, "Failed to restore $label", it)
        }
    }
}
