package com.ecosentinel.appblocker.data.repository

import android.content.Context

object ReportSettings {

    private const val PREFS_NAME = "report_settings"
    private const val KEY_ENABLED = "report_enabled"
    private const val KEY_INTERVAL_HOURS = "report_interval_hours"
    private const val KEY_CHAR_COUNT = "report_char_count"
    private const val KEY_MAX_SNOOZES = "report_max_snoozes"
    private const val KEY_SNOOZE_DURATION_MINS = "report_snooze_duration_mins"
    private const val KEY_CURRENT_SNOOZES = "report_current_snoozes"
    private const val KEY_SCHEDULE_TYPE = "report_schedule_type"
    private const val KEY_EXACT_TIMES = "report_exact_times"
    private const val KEY_EXPECTED_REPORT_TIME = "report_expected_time"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getIntervalHours(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_HOURS, 4)

    fun setIntervalHours(context: Context, hours: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL_HOURS, hours).apply()
    }

    fun getCharCount(context: Context): Int =
        prefs(context).getInt(KEY_CHAR_COUNT, 150)

    fun setCharCount(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_CHAR_COUNT, count).apply()
    }

    fun getMaxSnoozes(context: Context): Int =
        prefs(context).getInt(KEY_MAX_SNOOZES, 3)

    fun setMaxSnoozes(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_MAX_SNOOZES, count).apply()
    }

    fun getSnoozeDurationMins(context: Context): Int =
        prefs(context).getInt(KEY_SNOOZE_DURATION_MINS, 15)

    fun setSnoozeDurationMins(context: Context, mins: Int) {
        prefs(context).edit().putInt(KEY_SNOOZE_DURATION_MINS, mins).apply()
    }

    fun getCurrentSnoozes(context: Context): Int =
        prefs(context).getInt(KEY_CURRENT_SNOOZES, 0)

    fun incrementCurrentSnoozes(context: Context) {
        val current = getCurrentSnoozes(context)
        prefs(context).edit().putInt(KEY_CURRENT_SNOOZES, current + 1).apply()
    }

    fun resetCurrentSnoozes(context: Context) {
        prefs(context).edit().putInt(KEY_CURRENT_SNOOZES, 0).apply()
    }

    // 0 = Interval, 1 = Exact Times
    fun getScheduleType(context: Context): Int =
        prefs(context).getInt(KEY_SCHEDULE_TYPE, 0)

    fun setScheduleType(context: Context, type: Int) {
        prefs(context).edit().putInt(KEY_SCHEDULE_TYPE, type).apply()
    }

    fun getExactTimes(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXACT_TIMES, emptySet()) ?: emptySet()

    fun addExactTime(context: Context, time: String) {
        val times = getExactTimes(context).toMutableSet()
        times.add(time)
        prefs(context).edit().putStringSet(KEY_EXACT_TIMES, times).apply()
    }

    fun removeExactTime(context: Context, time: String) {
        val times = getExactTimes(context).toMutableSet()
        times.remove(time)
        prefs(context).edit().putStringSet(KEY_EXACT_TIMES, times).apply()
    }

    fun getExpectedReportTime(context: Context): Long =
        prefs(context).getLong(KEY_EXPECTED_REPORT_TIME, 0L)

    fun setExpectedReportTime(context: Context, timeMillis: Long) {
        prefs(context).edit().putLong(KEY_EXPECTED_REPORT_TIME, timeMillis).apply()
    }
}
