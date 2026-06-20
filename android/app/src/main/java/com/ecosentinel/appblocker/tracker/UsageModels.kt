package com.ecosentinel.appblocker.tracker

data class DailyUsageSummary(
    val dateKey: String,
    val label: String,
    val totalMillis: Long,
    val byPackage: Map<String, Long>
)

data class HourlyUsageBucket(
    val hour: Int,
    val millis: Long
)

data class PeakUsagePeriod(
    val startHour: Int,
    val endHour: Int,
    val millis: Long
)

data class CategoryUsage(
    val categoryName: String,
    val millis: Long
)

data class AppUsageDetail(
    val packageName: String,
    val label: String,
    val usedMillis: Long,
    val shareOfTotal: Float,
    val isSystemApp: Boolean
)

data class AppUsageSession(
    val startMillis: Long,
    val endMillis: Long,
    val durationMillis: Long
)

data class AppUsageDayDetail(
    val packageName: String,
    val dateKey: String,
    val totalMillis: Long,
    val sessionCount: Int,
    val averageSessionMillis: Long,
    val sessions: List<AppUsageSession>,
    val hourlyBuckets: List<HourlyUsageBucket>
)

data class WeeklyUsageSummary(
    val weekStartDateKey: String,
    val weekEndDateKey: String,
    val dayCount: Int,
    val dailySummaries: List<DailyUsageSummary>,
    val totalMillis: Long,
    val averageDailyMillis: Long,
    val byPackage: Map<String, Long>,
    val previousWeekTotalMillis: Long?,
    val busiestDay: DailyUsageSummary?,
    val lightestDay: DailyUsageSummary?,
    val topCategoryName: String?,
    val activeAppCount: Int
)

data class WeeklyAppUsageDetail(
    val packageName: String,
    val label: String,
    val totalMillis: Long,
    val averageDailyMillis: Long,
    val shareOfTotal: Float
)
