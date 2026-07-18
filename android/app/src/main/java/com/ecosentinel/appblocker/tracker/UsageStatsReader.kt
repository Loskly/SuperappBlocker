package com.ecosentinel.appblocker.tracker

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.os.Build

/**
 * Reads foreground time the same way Android Digital Wellbeing does: from [UsageStatsManager]
 * aggregated [UsageStats.totalTimeInForeground], not from raw [UsageEvents] totals.
 */
object UsageStatsReader {

    fun dailyUsageByPackage(
        usageStatsManager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        return queryStats(
            usageStatsManager = usageStatsManager,
            interval = UsageStatsManager.INTERVAL_DAILY,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }

    fun windowUsageByPackage(
        usageStatsManager: UsageStatsManager,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        if (endMillis <= startMillis) {
            return emptyMap()
        }
        return queryStats(
            usageStatsManager = usageStatsManager,
            interval = UsageStatsManager.INTERVAL_BEST,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }

    fun packageUsage(
        usageStatsManager: UsageStatsManager,
        packageName: String,
        startMillis: Long,
        endMillis: Long
    ): Long {
        return windowUsageByPackage(usageStatsManager, startMillis, endMillis)[packageName] ?: 0L
    }

    private fun queryStats(
        usageStatsManager: UsageStatsManager,
        interval: Int,
        startMillis: Long,
        endMillis: Long
    ): Map<String, Long> {
        val stats = usageStatsManager.queryUsageStats(interval, startMillis, endMillis)
            ?: return emptyMap()

        val maxMillisPerApp = (endMillis - startMillis + 1).coerceAtLeast(1L)
        val result = mutableMapOf<String, Long>()
        stats.forEach { usageStat ->
            if (!overlapsRange(usageStat, startMillis, endMillis)) {
                return@forEach
            }
            val millis = usageStat.totalTimeInForeground.coerceAtMost(maxMillisPerApp)
            if (millis <= 0L) {
                return@forEach
            }
            result[usageStat.packageName] = (result[usageStat.packageName] ?: 0L) + millis
        }
        return result.filterValues { it > 0L }
    }

    private fun overlapsRange(
        usageStat: UsageStats,
        startMillis: Long,
        endMillis: Long
    ): Boolean {
        if (usageStat.totalTimeInForeground <= 0L) {
            return false
        }
        if (usageStat.lastTimeUsed < startMillis) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (usageStat.firstTimeStamp > endMillis) {
                return false
            }
        }
        return true
    }
}
