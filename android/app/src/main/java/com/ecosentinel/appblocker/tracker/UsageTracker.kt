package com.ecosentinel.appblocker.tracker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.UsageDailyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class UsageTracker(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val database = AppDatabase.getInstance(context)

    fun todayKey(): String = DateKeys.today()

    fun isToday(dateKey: String): Boolean = dateKey == todayKey()

    fun minSelectableDateKey(): String = DateKeys.minSelectable()

    fun dateKeyToPickerUtcMillis(dateKey: String): Long {
        return DateKeys.toPickerUtcMillis(dateKey) ?: dateKeyToPickerUtcMillis(todayKey())
    }

    fun pickerUtcMillisToDateKey(utcMillis: Long): String = DateKeys.fromPickerUtcMillis(utcMillis)

    fun resolveDateKeyFromPicker(utcMillis: Long): String = DateKeys.resolveFromPicker(utcMillis)

    fun formatDisplayDate(dateKey: String): String {
        if (isToday(dateKey)) {
            return "Сегодня"
        }
        return DateKeys.formatDisplay(dateKey)
    }

    fun formatSectionDate(dateKey: String): String {
        if (isToday(dateKey)) {
            return "сегодня"
        }
        return DateKeys.formatDisplay(dateKey)
    }

    fun dateKeyForOffset(daysAgo: Int): String = DateKeys.offset(daysAgo)

    fun dayLabel(dateKey: String): String = DateKeys.dayLabel(dateKey)

    suspend fun syncTodayUsage(): Map<String, Long> = withContext(Dispatchers.IO) {
        syncDay(dateKeyForOffset(0))
    }

    suspend fun syncRecentHistory(days: Int = 7): List<DailyUsageSummary> = withContext(Dispatchers.IO) {
        val summaries = mutableListOf<DailyUsageSummary>()
        for (offset in days - 1 downTo 0) {
            val dateKey = dateKeyForOffset(offset)
            val usage = syncDay(dateKey)
            val total = usage.values.sum()
            summaries += DailyUsageSummary(
                dateKey = dateKey,
                label = if (offset == 0) "Сегодня" else dayLabel(dateKey),
                totalMillis = total,
                byPackage = usage
            )
        }
        summaries
    }

    suspend fun loadDailySummaries(days: Int = 7): List<DailyUsageSummary> = withContext(Dispatchers.IO) {
        syncRecentHistory(days)
    }

    /**
     * Single definition of "daily average" shared by the Stats screen and the weekly report so the
     * two never disagree. Averages only the COMPLETED days, like Digital Wellbeing, so the
     * still-in-progress current day does not drag the figure down. Falls back to including today
     * when it is the only day available.
     */
    fun dailyAverageMillis(summaries: List<DailyUsageSummary>): Long {
        if (summaries.isEmpty()) {
            return 0L
        }
        val completed = summaries.filterNot { isToday(it.dateKey) }
        val basis = completed.ifEmpty { summaries }
        return basis.sumOf { it.totalMillis } / basis.size
    }

    fun calendarWeekStartDateKey(forDateKey: String = todayKey()): String =
        DateKeys.weekStart(forDateKey)

    fun calendarWeekEndDateKey(weekStartDateKey: String): String =
        DateKeys.plusDays(weekStartDateKey, 6)

    fun effectiveWeekEndDateKey(weekStartDateKey: String, weekEndDateKey: String): String {
        val today = todayKey()
        return when {
            weekEndDateKey > today -> today
            weekEndDateKey < weekStartDateKey -> weekStartDateKey
            else -> weekEndDateKey
        }
    }

    fun previousWeekStartDateKey(weekStartDateKey: String): String =
        DateKeys.minusDays(weekStartDateKey, 7)

    fun dayBefore(dateKey: String): String = DateKeys.minusDays(dateKey, 1)

    fun formatWeekRange(startDateKey: String, endDateKey: String): String =
        DateKeys.formatWeekRange(startDateKey, endDateKey)

    suspend fun loadWeeklyReport(
        weekStartDateKey: String,
        weekEndDateKey: String
    ): WeeklyUsageSummary = withContext(Dispatchers.IO) {
        val effectiveEnd = effectiveWeekEndDateKey(weekStartDateKey, weekEndDateKey)
        val dateKeys = dateKeysBetween(weekStartDateKey, effectiveEnd)
        val dailySummaries = dateKeys.map { dateKey ->
            val usage = syncDay(dateKey)
            DailyUsageSummary(
                dateKey = dateKey,
                label = dayLabel(dateKey),
                totalMillis = usage.values.sum(),
                byPackage = usage
            )
        }

        val byPackage = mutableMapOf<String, Long>()
        dailySummaries.forEach { summary ->
            summary.byPackage.forEach { (packageName, millis) ->
                byPackage[packageName] = (byPackage[packageName] ?: 0L) + millis
            }
        }

        val totalMillis = dailySummaries.sumOf { it.totalMillis }
        val dayCount = dateKeys.size.coerceAtLeast(1)
        val averageDailyMillis = dailyAverageMillis(dailySummaries)

        val previousWeekStart = previousWeekStartDateKey(weekStartDateKey)
        val previousWeekEnd = dayBefore(weekStartDateKey)
        val previousWeekTotal = if (previousWeekEnd >= previousWeekStart) {
            dateKeysBetween(previousWeekStart, previousWeekEnd).sumOf { syncDay(it).values.sum() }
        } else {
            null
        }

        val busiestDay = dailySummaries.maxByOrNull { it.totalMillis }?.takeIf { it.totalMillis > 0L }
        val lightestDay = dailySummaries.filter { it.totalMillis > 0L }.minByOrNull { it.totalMillis }
        val topCategory = AppCategoryHelper.groupByCategory(
            context = context,
            usageByPackage = byPackage,
            includeHiddenSystemComponents = StatsDisplaySettings.showHiddenSystemComponents(context),
            hiddenSystemCategoryName = context.getString(R.string.stats_category_hidden_system)
        ).firstOrNull()
        val activeAppCount = byPackage.count { it.value > 0L }

        WeeklyUsageSummary(
            weekStartDateKey = weekStartDateKey,
            weekEndDateKey = effectiveEnd,
            dayCount = dayCount,
            dailySummaries = dailySummaries,
            totalMillis = totalMillis,
            averageDailyMillis = averageDailyMillis,
            byPackage = byPackage,
            previousWeekTotalMillis = previousWeekTotal,
            busiestDay = busiestDay,
            lightestDay = lightestDay,
            topCategoryName = topCategory?.categoryName,
            activeAppCount = activeAppCount
        )
    }

    fun toWeeklyAppDetails(
        usageByPackage: Map<String, Long>,
        dayCount: Int,
        includeHiddenSystemComponents: Boolean = StatsDisplaySettings.showHiddenSystemComponents(context)
    ): List<WeeklyAppUsageDetail> {
        val effectiveDays = dayCount.coerceAtLeast(1)
        return AppCategoryHelper.toAppDetails(context, usageByPackage, includeHiddenSystemComponents)
            .map { app ->
                WeeklyAppUsageDetail(
                    packageName = app.packageName,
                    label = app.label,
                    totalMillis = app.usedMillis,
                    averageDailyMillis = app.usedMillis / effectiveDays,
                    shareOfTotal = app.shareOfTotal
                )
            }
            .sortedByDescending { it.totalMillis }
    }

    suspend fun loadDaySummary(dateKey: String): DailyUsageSummary = withContext(Dispatchers.IO) {
        val usage = syncDay(dateKey)
        DailyUsageSummary(
            dateKey = dateKey,
            label = if (isToday(dateKey)) "Сегодня" else dayLabel(dateKey),
            totalMillis = usage.values.sum(),
            byPackage = usage
        )
    }

    suspend fun getTodayUsageMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        syncTodayUsage()
    }

    suspend fun getHourlyBucketsForToday(): List<HourlyUsageBucket> = withContext(Dispatchers.IO) {
        hourlyBucketsForDate(todayKey())
    }

    suspend fun getPeakPeriodForToday(): PeakUsagePeriod? = getPeakPeriodForDate(todayKey())

    suspend fun getPeakPeriodForDate(dateKey: String): PeakUsagePeriod? = withContext(Dispatchers.IO) {
        val buckets = hourlyBucketsForDate(dateKey)
        val peak = buckets.maxByOrNull { it.millis } ?: return@withContext null
        if (peak.millis <= 0L) return@withContext null
        PeakUsagePeriod(
            startHour = peak.hour,
            endHour = (peak.hour + 1) % 24,
            millis = peak.millis
        )
    }

    fun getForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val start = end - TimeUnit.SECONDS.toMillis(5)
        val events = usageStatsManager.queryEvents(start, end)
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            ) {
                lastPackage = event.packageName
            }
        }
        if (lastPackage != null) {
            return lastPackage
        }

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startOfDayMillis(todayKey()),
            end
        )
        return stats
            ?.filter { it.totalTimeInForeground > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    fun formatDuration(millis: Long): String = formatDurationStatic(millis)

    companion object {
        private const val SESSION_MERGE_GAP_MS = 2_000L

        fun formatDurationStatic(millis: Long): String {
            if (millis <= 0L) {
                return "0 сек"
            }
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 -> "${hours}ч ${minutes}мин"
                minutes > 0 -> "${minutes}мин"
                else -> "${seconds.coerceAtLeast(1)} сек"
            }
        }
    }

    fun formatHourRange(startHour: Int, endHour: Int): String {
        return String.format(Locale.getDefault(), "%02d:00–%02d:00", startHour, endHour)
    }

    fun formatTime(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    fun formatTimeRange(startMillis: Long, endMillis: Long): String {
        return "${formatTime(startMillis)} – ${formatTime(endMillis)}"
    }

    suspend fun getAppDayDetail(
        packageName: String,
        dateKey: String = todayKey()
    ): AppUsageDayDetail = withContext(Dispatchers.IO) {
        val start = startOfDayMillis(dateKey)
        val end = if (dateKey == todayKey()) {
            System.currentTimeMillis()
        } else {
            endOfDayMillis(dateKey)
        }
        parsePackageDayDetail(packageName, dateKey, start, end)
    }

    private fun parsePackageDayDetail(
        packageName: String,
        dateKey: String,
        startMillis: Long,
        endMillis: Long
    ): AppUsageDayDetail {
        val sessions = (buildSessionsByPackage(startMillis, endMillis)[packageName] ?: emptyList())
            .sortedByDescending { it.startMillis }

        // The app total must equal the sum of the sessions we display (Digital Wellbeing behaves
        // the same). Only when the system has already purged the raw events do we fall back to the
        // aggregated UsageStats value so the screen is not empty.
        val totalMillis = sessions.sumOf { it.durationMillis }.takeIf { it > 0L }
            ?: packageUsageInRange(packageName, startMillis, endMillis)

        val hourlyBuckets = LongArray(24)
        sessions.forEach { distributeSession(hourlyBuckets, it.startMillis, it.endMillis) }

        val sessionCount = sessions.size
        val averageSessionMillis = if (sessionCount > 0) {
            totalMillis / sessionCount
        } else {
            0L
        }

        return AppUsageDayDetail(
            packageName = packageName,
            dateKey = dateKey,
            totalMillis = totalMillis,
            sessionCount = sessionCount,
            averageSessionMillis = averageSessionMillis,
            sessions = sessions,
            hourlyBuckets = (0 until 24).map { hour -> HourlyUsageBucket(hour, hourlyBuckets[hour]) }
        )
    }

    private suspend fun syncDay(dateKey: String): Map<String, Long> {
        val start = startOfDayMillis(dateKey)
        val end = if (dateKey == todayKey()) {
            System.currentTimeMillis()
        } else {
            endOfDayMillis(dateKey)
        }
        val usageByPackage = queryUsageForRange(start, end)
        database.usageDailyDao().deleteForDate(dateKey)
        if (usageByPackage.isEmpty()) {
            return emptyMap()
        }
        usageByPackage.forEach { (packageName, millis) ->
            database.usageDailyDao().upsert(
                UsageDailyEntity(
                    id = "$dateKey:$packageName",
                    packageName = packageName,
                    dateKey = dateKey,
                    usedMillis = millis
                )
            )
        }
        return usageByPackage
    }

    private fun queryUsageForRange(startMillis: Long, endMillis: Long): Map<String, Long> {
        val fromEvents = sessionUsageByPackage(startMillis, endMillis)
        if (fromEvents.isNotEmpty()) {
            return fromEvents
        }
        // Days whose raw events the system has already purged: best-effort aggregate fallback.
        return UsageStatsReader.dailyUsageByPackage(usageStatsManager, startMillis, endMillis)
    }

    /**
     * Per-package foreground time derived from real session boundaries (same number/duration the
     * detail screen and Digital Wellbeing show), so every surface stays consistent. Works for any
     * range — full day or short enforcement window — because sessions are clipped to [startMillis,
     * endMillis] when they are built.
     */
    private fun sessionUsageByPackage(startMillis: Long, endMillis: Long): Map<String, Long> {
        return buildSessionsByPackage(startMillis, endMillis)
            .mapValues { (_, sessions) -> sessions.sumOf { it.durationMillis } }
            .filterValues { it > 0L }
    }

    /**
     * Single source of truth for sessions: walks the usage events once, opening a session when the
     * foreground package changes and closing it on background / screen-off / lock / shutdown, then
     * coalesces tiny same-app gaps.
     */
    private fun buildSessionsByPackage(
        startMillis: Long,
        endMillis: Long
    ): Map<String, List<AppUsageSession>> {
        val raw = HashMap<String, MutableList<AppUsageSession>>()

        var activePackage: String? = null
        var sessionStart = 0L

        fun closeSession(until: Long) {
            val pkg = activePackage
            if (pkg != null && sessionStart > 0L && until > sessionStart) {
                raw.getOrPut(pkg) { mutableListOf() } += AppUsageSession(
                    startMillis = sessionStart,
                    endMillis = until,
                    durationMillis = until - sessionStart
                )
            }
            activePackage = null
            sessionStart = 0L
        }

        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(startMillis, endMillis)

            when {
                event.isForegroundEvent() -> {
                    if (activePackage != event.packageName) {
                        closeSession(timestamp)
                        activePackage = event.packageName
                        sessionStart = timestamp
                    }
                }
                event.isBackgroundEvent() && activePackage == event.packageName -> {
                    closeSession(timestamp)
                }
                event.isSessionTerminatorEvent() -> {
                    closeSession(timestamp)
                }
            }
        }
        if (activePackage != null && sessionStart > 0L) {
            closeSession(endMillis)
        }

        return raw.mapValues { (_, sessions) -> coalesceSessions(sessions) }
    }

    private fun packageUsageInRange(
        packageName: String,
        startMillis: Long,
        endMillis: Long
    ): Long {
        return UsageStatsReader.packageUsage(
            usageStatsManager = usageStatsManager,
            packageName = packageName,
            startMillis = startMillis,
            endMillis = endMillis
        )
    }

    /**
     * Foreground time within an arbitrary (usually sub-day) window. Counts the real session time
     * inside the window from raw events — NOT [UsageStats.totalTimeInForeground], which reports the
     * whole day's aggregate and would massively over-report a short enforcement window.
     */
    private fun windowUsageByPackage(startMillis: Long, endMillis: Long): Map<String, Long> {
        if (endMillis <= startMillis) {
            return emptyMap()
        }
        return sessionUsageByPackage(startMillis, endMillis)
    }

    /**
     * Hourly distribution built from the SAME coalesced sessions used everywhere else
     * ([buildSessionsByPackage]). The bucket sum therefore equals the day's total exactly, so the
     * peak hour is real and no rescaling fudge is needed. Days whose events are already purged yield
     * empty buckets (no hourly granularity is recoverable from aggregated stats).
     */
    private fun hourlyBucketsForDate(dateKey: String): List<HourlyUsageBucket> {
        val start = startOfDayMillis(dateKey)
        val end = if (isToday(dateKey)) {
            System.currentTimeMillis()
        } else {
            endOfDayMillis(dateKey)
        }
        val buckets = LongArray(24)
        buildSessionsByPackage(start, end).values.forEach { sessions ->
            sessions.forEach { distributeSession(buckets, it.startMillis, it.endMillis) }
        }
        return (0 until 24).map { hour -> HourlyUsageBucket(hour, buckets[hour]) }
    }

    /**
     * Merges fragments of the same app that are separated only by tiny gaps (internal activity
     * transitions, brief pause/resume bounces). Without this a single continuous visit shows up as
     * many "fabricated" micro-sessions.
     */
    private fun coalesceSessions(sessions: List<AppUsageSession>): List<AppUsageSession> {
        if (sessions.size <= 1) {
            return sessions
        }
        val ascending = sessions.sortedBy { it.startMillis }
        val merged = ArrayList<AppUsageSession>(ascending.size)
        var current = ascending.first()
        for (index in 1 until ascending.size) {
            val next = ascending[index]
            val gap = next.startMillis - current.endMillis
            if (gap <= SESSION_MERGE_GAP_MS) {
                val newEnd = maxOf(current.endMillis, next.endMillis)
                current = current.copy(
                    endMillis = newEnd,
                    durationMillis = newEnd - current.startMillis
                )
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    private fun distributeSession(buckets: LongArray, startMillis: Long, endMillis: Long) {
        var cursor = startMillis
        while (cursor < endMillis) {
            val calendar = Calendar.getInstance().apply { timeInMillis = cursor }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val nextHour = calendar.timeInMillis.coerceAtMost(endMillis)
            buckets[hour] += nextHour - cursor
            cursor = nextHour
        }
    }

    private fun dateKeysBetween(startDateKey: String, endDateKey: String): List<String> =
        DateKeys.dateKeysBetween(startDateKey, endDateKey)

    private fun startOfDayMillis(dateKey: String): Long {
        return DateKeys.startOfDayMillis(dateKey) ?: DateKeys.startOfDayMillis(todayKey()) ?: 0L
    }

    private fun endOfDayMillis(dateKey: String): Long {
        return DateKeys.endOfDayMillis(dateKey)
            ?: DateKeys.endOfDayMillis(todayKey())
            ?: System.currentTimeMillis()
    }

    fun millisUntilMidnight(): Long = DateKeys.millisUntilMidnight()

    fun getPackageUsageInWindow(
        packageName: String,
        windowMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val startMillis = nowMillis - windowMinutes * 60_000L
        return windowUsageByPackage(startMillis, nowMillis)[packageName] ?: 0L
    }

    fun getCategoryUsageInWindow(
        categoryId: String,
        windowMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val startMillis = nowMillis - windowMinutes * 60_000L
        val usageByPackage = windowUsageByPackage(startMillis, nowMillis)
        return AppCategoryHelper.aggregateUsageForCategory(context, usageByPackage, categoryId)
    }

    fun getGroupUsageInWindow(
        groupId: String,
        windowMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val startMillis = nowMillis - windowMinutes * 60_000L
        val usageByPackage = windowUsageByPackage(startMillis, nowMillis)
        return AppGroupHelper.aggregateUsageForGroup(usageByPackage, groupId, context.packageName)
    }

    private fun UsageEvents.Event.isForegroundEvent(): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
            @Suppress("DEPRECATION")
            (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
    }

    private fun UsageEvents.Event.isBackgroundEvent(): Boolean {
        return eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
            eventType == UsageEvents.Event.ACTIVITY_STOPPED ||
            @Suppress("DEPRECATION")
            (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND)
    }

    /**
     * Screen-off / lock / shutdown end any foreground session, the same way Digital Wellbeing stops
     * counting. Prevents a session from spanning idle or powered-off periods.
     */
    private fun UsageEvents.Event.isSessionTerminatorEvent(): Boolean {
        return eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
            eventType == UsageEvents.Event.KEYGUARD_SHOWN ||
            eventType == UsageEvents.Event.DEVICE_SHUTDOWN
    }
}
