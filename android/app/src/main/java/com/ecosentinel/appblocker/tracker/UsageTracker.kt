package com.ecosentinel.appblocker.tracker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.UsageDailyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class UsageTracker(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val database = AppDatabase.getInstance(context)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayLabelFormat = SimpleDateFormat("EEE", Locale("ru"))
    private val displayDateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val utcTimeZone = TimeZone.getTimeZone("UTC")

    fun todayKey(): String = dateKeyForOffset(0)

    fun isToday(dateKey: String): Boolean = dateKey == todayKey()

    fun minSelectableDateKey(): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        return dateFormat.format(calendar.time)
    }

    fun dateKeyToPickerUtcMillis(dateKey: String): Long {
        val local = calendarForDateKey(dateKey) ?: return pickerUtcMillisForToday()
        val utc = Calendar.getInstance(utcTimeZone).apply {
            set(Calendar.YEAR, local.get(Calendar.YEAR))
            set(Calendar.MONTH, local.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, local.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return utc.timeInMillis
    }

    fun pickerUtcMillisToDateKey(utcMillis: Long): String {
        val utc = Calendar.getInstance(utcTimeZone).apply {
            timeInMillis = utcMillis
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH) + 1,
            utc.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatDisplayDate(dateKey: String): String {
        if (isToday(dateKey)) {
            return "Сегодня"
        }
        val calendar = calendarForDateKey(dateKey) ?: return dateKey
        return displayDateFormat.format(calendar.time)
    }

    fun formatSectionDate(dateKey: String): String {
        if (isToday(dateKey)) {
            return "сегодня"
        }
        val calendar = calendarForDateKey(dateKey) ?: return dateKey
        return displayDateFormat.format(calendar.time)
    }

    fun dateKeyForOffset(daysAgo: Int): String {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        return dateFormat.format(calendar.time)
    }

    fun dayLabel(dateKey: String): String {
        val calendar = calendarForDateKey(dateKey) ?: return dateKey
        return dayLabelFormat.format(calendar.time)
    }

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

    fun calendarWeekStartDateKey(forDateKey: String = todayKey()): String {
        val calendar = calendarForDateKey(forDateKey) ?: return forDateKey
        val daysFromMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        return dateFormat.format(calendar.time)
    }

    fun calendarWeekEndDateKey(weekStartDateKey: String): String {
        val calendar = calendarForDateKey(weekStartDateKey) ?: return weekStartDateKey
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        return dateFormat.format(calendar.time)
    }

    fun effectiveWeekEndDateKey(weekStartDateKey: String, weekEndDateKey: String): String {
        val today = todayKey()
        return when {
            weekEndDateKey > today -> today
            weekEndDateKey < weekStartDateKey -> weekStartDateKey
            else -> weekEndDateKey
        }
    }

    fun previousWeekStartDateKey(weekStartDateKey: String): String {
        val calendar = calendarForDateKey(weekStartDateKey) ?: return weekStartDateKey
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        return dateFormat.format(calendar.time)
    }

    fun dayBefore(dateKey: String): String {
        val calendar = calendarForDateKey(dateKey) ?: return dateKey
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return dateFormat.format(calendar.time)
    }

    fun formatWeekRange(startDateKey: String, endDateKey: String): String {
        val startCal = calendarForDateKey(startDateKey)
        val endCal = calendarForDateKey(endDateKey)
        if (startCal == null || endCal == null) {
            return "$startDateKey – $endDateKey"
        }
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val endDay = endCal.get(Calendar.DAY_OF_MONTH)
        val startMonth = SimpleDateFormat("MMMM", Locale("ru")).format(startCal.time)
        val endMonth = SimpleDateFormat("MMMM", Locale("ru")).format(endCal.time)
        val year = endCal.get(Calendar.YEAR)
        return if (startMonth == endMonth) {
            "$startDay–$endDay $endMonth $year"
        } else {
            "$startDay $startMonth – $endDay $endMonth $year"
        }
    }

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
        val averageDailyMillis = if (dayCount > 0) totalMillis / dayCount else 0L

        val previousWeekStart = previousWeekStartDateKey(weekStartDateKey)
        val previousWeekEnd = dayBefore(weekStartDateKey)
        val previousWeekTotal = if (previousWeekEnd >= previousWeekStart) {
            dateKeysBetween(previousWeekStart, previousWeekEnd).sumOf { syncDay(it).values.sum() }
        } else {
            null
        }

        val busiestDay = dailySummaries.maxByOrNull { it.totalMillis }?.takeIf { it.totalMillis > 0L }
        val lightestDay = dailySummaries.filter { it.totalMillis > 0L }.minByOrNull { it.totalMillis }
        val topCategory = AppCategoryHelper.groupByCategory(context, byPackage).firstOrNull()
        val activeAppCount = byPackage.count { it.value > 0L && it.key != context.packageName }

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
        dayCount: Int
    ): List<WeeklyAppUsageDetail> {
        val total = usageByPackage.values.sum().coerceAtLeast(1L)
        val effectiveDays = dayCount.coerceAtLeast(1)
        return AppCategoryHelper.toAppDetails(context, usageByPackage, includeSystemApps = false)
            .map { app ->
                WeeklyAppUsageDetail(
                    packageName = app.packageName,
                    label = app.label,
                    totalMillis = app.usedMillis,
                    averageDailyMillis = app.usedMillis / effectiveDays,
                    shareOfTotal = app.usedMillis.toFloat() / total.toFloat()
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
        val start = startOfDayMillis(dateKeyForOffset(0))
        val end = System.currentTimeMillis()
        val buckets = parseUsageEvents(start, end, collectHourly = true).hourlyBuckets ?: LongArray(24)
        (0 until 24).map { hour -> HourlyUsageBucket(hour, buckets[hour]) }
    }

    suspend fun getPeakPeriodForToday(): PeakUsagePeriod? = getPeakPeriodForDate(todayKey())

    suspend fun getPeakPeriodForDate(dateKey: String): PeakUsagePeriod? = withContext(Dispatchers.IO) {
        val start = startOfDayMillis(dateKey)
        val end = if (isToday(dateKey)) {
            System.currentTimeMillis()
        } else {
            endOfDayMillis(dateKey)
        }
        val buckets = parseUsageEvents(start, end, collectHourly = true).hourlyBuckets ?: LongArray(24)
        val peak = (0 until 24)
            .map { hour -> HourlyUsageBucket(hour, buckets[hour]) }
            .maxByOrNull { it.millis }
            ?: return@withContext null
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
            calendarStartOfToday(),
            end
        )
        return stats
            ?.filter { it.totalTimeInForeground > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> "${hours}ч ${minutes}мин"
            minutes > 0 -> "${minutes}мин"
            else -> "< 1мин"
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
        val sessions = mutableListOf<AppUsageSession>()
        val hourlyBuckets = LongArray(24)

        var activePackage: String? = null
        var sessionStart = 0L

        fun closeSession(until: Long) {
            val pkg = activePackage ?: return
            if (sessionStart <= 0L || until <= sessionStart) {
                activePackage = null
                sessionStart = 0L
                return
            }
            if (pkg == packageName) {
                val duration = until - sessionStart
                sessions += AppUsageSession(
                    startMillis = sessionStart,
                    endMillis = until,
                    durationMillis = duration
                )
                distributeSession(hourlyBuckets, sessionStart, until)
            }
            activePackage = null
            sessionStart = 0L
        }

        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(startMillis, endMillis)
            val isForeground = event.isForegroundEvent()
            val isBackground = event.isBackgroundEvent()

            when {
                isForeground -> {
                    closeSession(timestamp)
                    activePackage = event.packageName
                    sessionStart = timestamp
                }
                isBackground && activePackage == event.packageName -> {
                    closeSession(timestamp)
                }
            }
        }
        if (activePackage == packageName && sessionStart > 0L) {
            closeSession(endMillis)
        }

        val sortedSessions = sessions
            .filter { it.durationMillis > 0L }
            .sortedByDescending { it.startMillis }
        val totalMillis = sortedSessions.sumOf { it.durationMillis }
        val sessionCount = sortedSessions.size
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
            sessions = sortedSessions,
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
        if (usageByPackage.isEmpty()) {
            val cached = loadCachedUsageForDate(dateKey)
            if (cached.isNotEmpty()) {
                return cached
            }
            return emptyMap()
        }
        database.usageDailyDao().deleteForDate(dateKey)
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

    private suspend fun loadCachedUsageForDate(dateKey: String): Map<String, Long> {
        return database.usageDailyDao().getForDate(dateKey)
            .associate { it.packageName to it.usedMillis }
    }

    private fun queryUsageForRange(startMillis: Long, endMillis: Long): Map<String, Long> {
        val fromEvents = parseUsageEvents(startMillis, endMillis).usageByPackage
        if (fromEvents.isNotEmpty()) {
            return fromEvents
        }
        return queryUsageStatsBest(startMillis, endMillis)
    }

    private fun queryUsageStatsBest(startMillis: Long, endMillis: Long): Map<String, Long> {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            startMillis,
            endMillis
        ) ?: return emptyMap()

        return stats
            .filter { it.totalTimeInForeground > 0 && it.packageName != context.packageName }
            .associate { it.packageName to it.totalTimeInForeground }
    }

    private data class EventParseResult(
        val usageByPackage: Map<String, Long>,
        val hourlyBuckets: LongArray? = null
    )

    private fun parseUsageEvents(
        startMillis: Long,
        endMillis: Long,
        collectHourly: Boolean = false
    ): EventParseResult {
        val usageByPackage = mutableMapOf<String, Long>()
        val buckets = if (collectHourly) LongArray(24) else null

        var activePackage: String? = null
        var sessionStart = 0L

        fun closeSession(until: Long) {
            val pkg = activePackage ?: return
            if (sessionStart <= 0L || until <= sessionStart) {
                activePackage = null
                sessionStart = 0L
                return
            }
            if (pkg != context.packageName) {
                val duration = until - sessionStart
                usageByPackage[pkg] = (usageByPackage[pkg] ?: 0L) + duration
                buckets?.let { distributeSession(it, sessionStart, until) }
            }
            activePackage = null
            sessionStart = 0L
        }

        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val timestamp = event.timeStamp.coerceIn(startMillis, endMillis)
            val isForeground = event.isForegroundEvent()
            val isBackground = event.isBackgroundEvent()

            when {
                isForeground -> {
                    closeSession(timestamp)
                    activePackage = event.packageName
                    sessionStart = timestamp
                }
                isBackground && activePackage == event.packageName -> {
                    closeSession(timestamp)
                }
            }
        }
        if (activePackage != null && sessionStart > 0L) {
            closeSession(endMillis)
        }

        return EventParseResult(
            usageByPackage = usageByPackage.filterValues { it > 0L },
            hourlyBuckets = buckets
        )
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

    private fun dateKeysBetween(startDateKey: String, endDateKey: String): List<String> {
        val start = calendarForDateKey(startDateKey) ?: return emptyList()
        val end = calendarForDateKey(endDateKey) ?: return emptyList()
        if (end.before(start)) {
            return emptyList()
        }
        val keys = mutableListOf<String>()
        val cursor = start.clone() as Calendar
        while (!cursor.after(end)) {
            keys += dateFormat.format(cursor.time)
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return keys
    }

    private fun calendarForDateKey(dateKey: String): Calendar? {
        val parts = dateKey.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull()?.minus(1) ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun startOfDayMillis(dateKey: String): Long {
        return calendarForDateKey(dateKey)?.timeInMillis ?: calendarStartOfToday()
    }

    private fun endOfDayMillis(dateKey: String): Long {
        return Calendar.getInstance().apply {
            timeInMillis = startOfDayMillis(dateKey)
            add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.MILLISECOND, -1)
        }.timeInMillis
    }

    private fun calendarStartOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun pickerUtcMillisForToday(): Long {
        return dateKeyToPickerUtcMillis(todayKey())
    }

    fun millisUntilMidnight(): Long {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return midnight.timeInMillis - now.timeInMillis
    }

    fun getPackageUsageInWindow(
        packageName: String,
        windowMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val startMillis = nowMillis - windowMinutes * 60_000L
        return parseUsageEvents(startMillis, nowMillis).usageByPackage[packageName] ?: 0L
    }

    fun getCategoryUsageInWindow(
        categoryId: String,
        windowMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val startMillis = nowMillis - windowMinutes * 60_000L
        val usageByPackage = parseUsageEvents(startMillis, nowMillis).usageByPackage
        return AppCategoryHelper.aggregateUsageForCategory(context, usageByPackage, categoryId)
    }

    fun getGroupUsageInWindow(
        groupId: String,
        windowMinutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val startMillis = nowMillis - windowMinutes * 60_000L
        val usageByPackage = parseUsageEvents(startMillis, nowMillis).usageByPackage
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
}
