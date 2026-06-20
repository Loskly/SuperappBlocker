package com.ecosentinel.appblocker.focus

import android.content.Context
import org.json.JSONArray

class FocusConfigStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedCategoryIds(): Set<String> {
        return prefs.getString(KEY_CATEGORIES, null).fromJsonArray().toSet()
    }

    fun setSelectedCategoryIds(ids: Set<String>) {
        prefs.edit().putString(KEY_CATEGORIES, ids.toJsonArray()).apply()
    }

    fun getSelectedPackages(): Set<String> {
        return prefs.getString(KEY_PACKAGES, null).fromJsonArray().toSet()
    }

    fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putString(KEY_PACKAGES, packages.toJsonArray()).apply()
    }

    fun getDurationMinutes(): Int {
        return prefs.getInt(KEY_DURATION, DEFAULT_DURATION_MINUTES)
    }

    fun setDurationMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DURATION, minutes).apply()
    }

    fun getMode(): FocusMode {
        val raw = prefs.getString(KEY_MODE, FocusMode.SOFT.name) ?: FocusMode.SOFT.name
        return runCatching { FocusMode.valueOf(raw) }.getOrDefault(FocusMode.SOFT)
    }

    fun setMode(mode: FocusMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun saveDraft(
        categoryIds: Set<String>,
        packages: Set<String>,
        durationMinutes: Int,
        mode: FocusMode
    ) {
        prefs.edit()
            .putString(KEY_CATEGORIES, categoryIds.toJsonArray())
            .putString(KEY_PACKAGES, packages.toJsonArray())
            .putInt(KEY_DURATION, durationMinutes)
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "focus_config"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_DURATION = "duration_minutes"
        private const val KEY_MODE = "mode"
        const val DEFAULT_DURATION_MINUTES = 25

        val DURATION_PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)

        private fun Set<String>.toJsonArray(): String {
            val array = JSONArray()
            forEach { array.put(it) }
            return array.toString()
        }

        private fun String?.fromJsonArray(): List<String> {
            if (this.isNullOrBlank()) {
                return emptyList()
            }
            return try {
                val array = JSONArray(this)
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index)
                        if (value.isNotBlank()) {
                            add(value)
                        }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
