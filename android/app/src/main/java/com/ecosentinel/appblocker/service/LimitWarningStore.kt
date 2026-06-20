package com.ecosentinel.appblocker.service

import android.content.Context

class LimitWarningStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun wasWarned(dateKey: String, ruleId: String): Boolean {
        return prefs.getBoolean(warningKey(dateKey, ruleId), false)
    }

    fun markWarned(dateKey: String, ruleId: String) {
        prefs.edit().putBoolean(warningKey(dateKey, ruleId), true).apply()
    }

    fun clearExceptDate(dateKey: String) {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { key -> !key.startsWith("$dateKey:") }
            .forEach { key -> editor.remove(key) }
        editor.apply()
    }

    private fun warningKey(dateKey: String, ruleId: String): String = "$dateKey:$ruleId"

    companion object {
        private const val PREFS_NAME = "limit_warnings"
    }
}
