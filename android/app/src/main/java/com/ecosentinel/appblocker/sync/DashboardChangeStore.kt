package com.ecosentinel.appblocker.sync

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class DashboardChangeStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val changeListType = Types.newParameterizedType(List::class.java, DashboardChange::class.java)
    private val adapter = moshi.adapter<List<DashboardChange>>(changeListType)

    fun saveChanges(changes: List<DashboardChange>) {
        prefs.edit()
            .putString(KEY_CHANGES_JSON, adapter.toJson(changes.take(MAX_CHANGES)))
            .apply()
    }

    fun getChanges(): List<DashboardChange> {
        val json = prefs.getString(KEY_CHANGES_JSON, null) ?: return emptyList()
        return runCatching { adapter.fromJson(json).orEmpty() }.getOrDefault(emptyList())
    }

    data class DashboardChange(
        val id: Int,
        val eventType: String,
        val summary: String,
        val detailJson: String?,
        val createdAt: String
    )

    companion object {
        private const val PREFS_NAME = "dashboard_change_store"
        private const val KEY_CHANGES_JSON = "changes_json"
        private const val MAX_CHANGES = 20
    }
}
