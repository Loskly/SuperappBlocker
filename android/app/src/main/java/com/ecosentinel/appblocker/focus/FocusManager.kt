package com.ecosentinel.appblocker.focus

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.FocusSessionEntity
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import org.json.JSONArray

class FocusManager(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).focusSessionDao()

    suspend fun getActiveSession(nowMillis: Long = System.currentTimeMillis()): FocusSessionEntity? {
        expireIfNeeded(nowMillis)
        val session = dao.getSession() ?: return null
        if (!session.active || session.expiresAtMillis <= nowMillis) {
            return null
        }
        return session
    }

    suspend fun startFocus(
        blockedCategoryIds: Set<String>,
        blockedPackages: Set<String>,
        durationMinutes: Int,
        mode: FocusMode
    ) {
        require(durationMinutes > 0) { "durationMinutes must be positive" }
        require(blockedCategoryIds.isNotEmpty() || blockedPackages.isNotEmpty()) {
            "Select at least one category or app"
        }

        val now = System.currentTimeMillis()
        val session = FocusSessionEntity(
            active = true,
            mode = mode,
            startedAtMillis = now,
            expiresAtMillis = now + durationMinutes * 60_000L,
            blockedCategoryIdsJson = blockedCategoryIds.toJsonArray(),
            blockedPackagesJson = blockedPackages.toJsonArray()
        )
        dao.upsert(session)
        FocusNotificationHelper.show(appContext, session)
        FocusExpireScheduler.schedule(appContext, session.expiresAtMillis)
    }

    suspend fun stopFocus(): Boolean {
        val session = dao.getSession() ?: return false
        if (!session.active || session.mode != FocusMode.SOFT) {
            return false
        }
        dao.upsert(session.copy(active = false))
        FocusNotificationHelper.cancel(appContext)
        FocusExpireScheduler.cancel(appContext)
        return true
    }

    suspend fun stopFocusFromRemote(): Boolean {
        val session = dao.getSession() ?: return false
        if (!session.active) {
            return false
        }
        dao.upsert(session.copy(active = false))
        FocusNotificationHelper.cancel(appContext)
        FocusExpireScheduler.cancel(appContext)
        return true
    }

    suspend fun expireIfNeeded(nowMillis: Long = System.currentTimeMillis()) {
        val session = dao.getSession() ?: return
        if (!session.active) {
            return
        }
        if (session.expiresAtMillis <= nowMillis) {
            dao.upsert(session.copy(active = false))
            FocusNotificationHelper.cancel(appContext)
            FocusExpireScheduler.cancel(appContext)
        }
    }

    fun isPackageBlocked(
        context: Context,
        packageName: String,
        session: FocusSessionEntity
    ): Boolean {
        val blockedPackages = session.blockedPackagesJson.fromJsonArray().toSet()
        if (packageName in blockedPackages) {
            return true
        }
        val blockedCategories = session.blockedCategoryIdsJson.fromJsonArray().toSet()
        if (blockedCategories.isEmpty()) {
            return false
        }
        val categoryId = AppCategoryHelper.categoryIdForPackage(context, packageName)
        return categoryId in blockedCategories
    }

    fun millisUntilFocusEnds(session: FocusSessionEntity, nowMillis: Long = System.currentTimeMillis()): Long {
        return (session.expiresAtMillis - nowMillis).coerceAtLeast(0L)
    }

    fun parseStringList(json: String): List<String> = json.fromJsonArray()

    companion object {
        fun Set<String>.toJsonArray(): String {
            val array = JSONArray()
            forEach { array.put(it) }
            return array.toString()
        }

        fun String.fromJsonArray(): List<String> {
            if (isBlank()) {
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
