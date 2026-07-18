package com.ecosentinel.appblocker.service

/**
 * Throttles expensive full-day [com.ecosentinel.appblocker.tracker.UsageTracker.syncTodayUsage]
 * calls in the monitor loop while the screen is on. When the screen is off the loop sleeps entirely
 * (see [MonitorScreenGate]).
 *
 * Refreshes when:
 * - cache is empty (first tick / after invalidation);
 * - [SYNC_INTERVAL_MS] elapsed since the last full sync;
 * - the foreground package changed (so daily limits are re-checked on app switch).
 */
class TodayUsageSyncCache {

    private var cachedUsage: Map<String, Long> = emptyMap()
    private var cachedDateKey: String? = null
    private var lastSyncAtMillis: Long = 0L
    private var lastForegroundPackage: String? = null

    fun needsSync(nowMillis: Long, foregroundPackage: String?): Boolean {
        if (cachedUsage.isEmpty()) {
            return true
        }
        if (nowMillis - lastSyncAtMillis >= SYNC_INTERVAL_MS) {
            return true
        }
        if (foregroundPackage != null && foregroundPackage != lastForegroundPackage) {
            return true
        }
        return false
    }

    fun recordSync(
        usage: Map<String, Long>,
        foregroundPackage: String?,
        nowMillis: Long,
        dateKey: String
    ) {
        cachedUsage = usage
        cachedDateKey = dateKey
        lastSyncAtMillis = nowMillis
        lastForegroundPackage = foregroundPackage
    }

    fun invalidateIfDayChanged(todayKey: String) {
        if (cachedDateKey != null && cachedDateKey != todayKey) {
            invalidate()
        }
    }

    fun cachedUsage(): Map<String, Long> = cachedUsage

    fun invalidate() {
        cachedUsage = emptyMap()
        cachedDateKey = null
        lastSyncAtMillis = 0L
        lastForegroundPackage = null
    }

    companion object {
        const val SYNC_INTERVAL_MS = 60_000L
    }
}
