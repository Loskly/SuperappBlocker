package com.ecosentinel.appblocker.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.PolicyEngine
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.receiver.ScreenOffReceiver
import com.ecosentinel.appblocker.security.PasswordSessionManager
import com.ecosentinel.appblocker.security.AppPasswordStore
import com.ecosentinel.appblocker.sync.SyncWorker
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.ui.BlockOverlayManager
import com.ecosentinel.appblocker.ui.PasswordOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var monitorJob: Job? = null

    private lateinit var usageTracker: UsageTracker
    private lateinit var policyEngine: PolicyEngine
    private lateinit var passwordStore: AppPasswordStore
    private lateinit var focusManager: FocusManager
    private lateinit var limitWarningChecker: LimitWarningChecker
    private lateinit var cooldownManager: CooldownManager
    private var screenOffReceiver: ScreenOffReceiver? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        usageTracker = UsageTracker(this)
        policyEngine = PolicyEngine(this)
        passwordStore = AppPasswordStore(this)
        focusManager = FocusManager(this)
        limitWarningChecker = LimitWarningChecker(this)
        cooldownManager = CooldownManager(this)
        registerScreenOffReceiver()
        MonitorNotificationHelper.createChannel(this)
        LimitWarningNotificationHelper.createChannel(this)
        startForeground(
            MonitorNotificationHelper.NOTIFICATION_ID,
            MonitorNotificationHelper.buildNotification(this)
        )
        SyncWorker.enqueueOneTime(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            MonitorNotificationHelper.NOTIFICATION_ID,
            MonitorNotificationHelper.buildNotification(this)
        )
        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        MonitorBootstrap.ensureMonitoring(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        unregisterScreenOffReceiver()
        BlockOverlayManager.hide(this)
        PasswordOverlayManager.hide(this)
        monitorJob?.cancel()
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        MonitorNotificationHelper.cancel(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        while (scope.isActive) {
            focusManager.expireIfNeeded()
            val usageMap = usageTracker.syncTodayUsage()
            AppGroupHelper.ensureCacheLoaded(this@UsageMonitorService)
            cooldownManager.updateCooldownStates()
            limitWarningChecker.checkAndNotify(usageMap)
            val foreground = usageTracker.getForegroundPackage()
            val browserUrl = foreground?.let {
                com.ecosentinel.appblocker.modules.adult.BrowserUrlState.currentUrl(it)
            }
            val inAppFeature = foreground?.let {
                com.ecosentinel.appblocker.modules.inapp.InAppFeatureState.currentFeature(it)
            }
            PasswordSessionManager.onForegroundChanged(
                foreground,
                passwordStore.getUnlockMode()
            )

            val context = BlockContext(
                foregroundPackage = foreground,
                usageMillisToday = usageMap,
                currentBrowserUrl = browserUrl,
                currentInAppFeature = inAppFeature
            )
            val passwordRequired = policyEngine.shouldRequirePassword(context)
            val blockDecision = policyEngine.shouldBlock(context)

            when {
                passwordRequired && foreground != null -> {
                    BlockOverlayManager.hide(this)
                    PasswordOverlayManager.show(this, foreground)
                }
                blockDecision != null && foreground != null -> {
                    PasswordOverlayManager.hide(this)
                    BlockOverlayManager.show(this, foreground, blockDecision.reason)
                }
                else -> {
                    BlockOverlayManager.hide(this)
                    PasswordOverlayManager.hide(this)
                }
            }

            delay(POLL_INTERVAL_MS)
        }
    }

    private fun registerScreenOffReceiver() {
        if (screenOffReceiver != null) {
            return
        }
        screenOffReceiver = ScreenOffReceiver()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun unregisterScreenOffReceiver() {
        val receiver = screenOffReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        screenOffReceiver = null
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L

        @Volatile
        private var running = false

        fun isRunning(context: Context): Boolean {
            if (running) {
                return true
            }
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Int.MAX_VALUE)
                .any { it.service.className == UsageMonitorService::class.java.name }
        }

        fun start(context: Context) {
            if (!MonitorBootstrap.canRunMonitoring(context)) {
                return
            }
            val intent = Intent(context, UsageMonitorService::class.java)
            context.startForegroundService(intent)
        }
    }
}
