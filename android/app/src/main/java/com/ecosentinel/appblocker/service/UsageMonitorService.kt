package com.ecosentinel.appblocker.service

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.PolicyEngine
import com.ecosentinel.appblocker.focus.FocusExpireScheduler
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.focus.FocusNotificationHelper
import com.ecosentinel.appblocker.receiver.MonitorScreenReceiver
import com.ecosentinel.appblocker.security.AppPasswordStore
import com.ecosentinel.appblocker.security.PasswordSessionManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private lateinit var powerManager: PowerManager
    private var screenReceiver: MonitorScreenReceiver? = null
    private val todayUsageCache = TodayUsageSyncCache()

    override fun onCreate() {
        super.onCreate()
        running = true
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        MonitorScreenGate.init(powerManager.isInteractive)
        usageTracker = UsageTracker(this)
        policyEngine = PolicyEngine(this)
        passwordStore = AppPasswordStore(this)
        focusManager = FocusManager(this)
        limitWarningChecker = LimitWarningChecker(this)
        cooldownManager = CooldownManager(this)
        registerScreenReceiver()
        MonitorNotificationHelper.createChannel(this)
        LimitWarningNotificationHelper.createChannel(this)
        FocusNotificationHelper.createChannel(this)
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
        scope.launch {
            val session = focusManager.getActiveSession()
            FocusNotificationHelper.sync(this@UsageMonitorService, session)
            if (session != null) {
                FocusExpireScheduler.schedule(this@UsageMonitorService, session.expiresAtMillis)
            } else {
                FocusExpireScheduler.cancel(this@UsageMonitorService)
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        MonitorBootstrap.ensureMonitoring(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        unregisterScreenReceiver()
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
        MonitorScreenGate.init(powerManager.isInteractive)

        while (scope.isActive) {
            if (!MonitorScreenGate.screenInteractive) {
                awaitScreenWake()
                runWakeUpTick()
                continue
            }

            runMonitorTick()

            if (MonitorScreenGate.awaitSleepOrDelay(POLL_INTERVAL_MS)) {
                continue
            }
        }
    }

    private suspend fun awaitScreenWake() {
        while (scope.isActive && !MonitorScreenGate.screenInteractive) {
            val receivedWake = withTimeoutOrNull(MonitorScreenGate.SCREEN_WAKE_FALLBACK_MS) {
                MonitorScreenGate.awaitWake()
                true
            }
            if (MonitorScreenGate.screenInteractive) {
                return
            }
            if (receivedWake == null && powerManager.isInteractive) {
                MonitorScreenGate.notifyScreenWake()
                return
            }
        }
    }

    private suspend fun runWakeUpTick() {
        val todayKey = usageTracker.todayKey()
        todayUsageCache.invalidateIfDayChanged(todayKey)
        todayUsageCache.invalidate()

        focusManager.expireIfNeeded()
        AppGroupHelper.ensureCacheLoaded(this@UsageMonitorService)
        cooldownManager.updateCooldownStates()

        val foreground = usageTracker.getForegroundPackage()
        val nowMillis = System.currentTimeMillis()
        val usage = usageTracker.syncTodayUsage()
        todayUsageCache.recordSync(usage, foreground, nowMillis, todayKey)
        limitWarningChecker.checkAndNotify(usage)
        applyBlockPolicy(foreground, usage, nowMillis)
    }

    private suspend fun runMonitorTick() {
        focusManager.expireIfNeeded()
        AppGroupHelper.ensureCacheLoaded(this@UsageMonitorService)
        cooldownManager.updateCooldownStates()

        val foreground = usageTracker.getForegroundPackage()
        val nowMillis = System.currentTimeMillis()
        val usageRefreshed = refreshTodayUsageIfNeeded(foreground, nowMillis)
        val usageMap = todayUsageCache.cachedUsage()

        if (usageRefreshed) {
            limitWarningChecker.checkAndNotify(usageMap)
        }

        applyBlockPolicy(foreground, usageMap, nowMillis)
    }

    private fun applyBlockPolicy(
        foreground: String?,
        usageMap: Map<String, Long>,
        nowMillis: Long
    ) {
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
            currentInAppFeature = inAppFeature,
            nowMillis = nowMillis
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
    }

    private suspend fun refreshTodayUsageIfNeeded(
        foregroundPackage: String?,
        nowMillis: Long
    ): Boolean {
        val todayKey = usageTracker.todayKey()
        todayUsageCache.invalidateIfDayChanged(todayKey)
        if (!todayUsageCache.needsSync(nowMillis, foregroundPackage)) {
            return false
        }
        val usage = usageTracker.syncTodayUsage()
        todayUsageCache.recordSync(usage, foregroundPackage, nowMillis, todayKey)
        return true
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) {
            return
        }
        screenReceiver = MonitorScreenReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenReceiver() {
        val receiver = screenReceiver ?: return
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        screenReceiver = null
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L

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
