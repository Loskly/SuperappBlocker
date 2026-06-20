package com.ecosentinel.appblocker.tracker

import android.content.Context

object WeeklyReportSettings {

    private const val PREFS_NAME = "weekly_report_settings"
    private const val KEY_LAST_SENT_WEEK_START = "last_sent_week_start"

    fun getLastSentWeekStart(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SENT_WEEK_START, null)
    }

    fun setLastSentWeekStart(context: Context, weekStartDateKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SENT_WEEK_START, weekStartDateKey)
            .apply()
    }
}
