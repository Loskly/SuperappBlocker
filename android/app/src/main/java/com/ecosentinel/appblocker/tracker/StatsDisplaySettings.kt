package com.ecosentinel.appblocker.tracker

import android.content.Context

object StatsDisplaySettings {

    private const val PREFS_NAME = "stats_display_settings"
    private const val KEY_SHOW_HIDDEN_SYSTEM = "show_hidden_system"

    fun showHiddenSystemComponents(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_HIDDEN_SYSTEM, false)
    }

    fun setShowHiddenSystemComponents(context: Context, show: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_HIDDEN_SYSTEM, show)
            .apply()
    }
}
