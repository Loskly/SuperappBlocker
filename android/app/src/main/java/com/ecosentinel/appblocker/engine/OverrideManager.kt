package com.ecosentinel.appblocker.engine

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.OverrideStateEntity
import com.ecosentinel.appblocker.data.entity.UnlockGrantEntity
import kotlinx.coroutines.runBlocking

class OverrideManager(context: Context) {

    private val database = AppDatabase.getInstance(context)
    private val overrideDao = database.overrideStateDao()
    private val unlockGrantDao = database.unlockGrantDao()

    fun isOverrideActive(nowMillis: Long = System.currentTimeMillis()): Boolean = runBlocking {
        val state = overrideDao.getState()
        state?.active == true && (state.untilMillis == null || state.untilMillis > nowMillis)
    }

    suspend fun activateSuperPasswordOverride(durationMinutes: Int) {
        val until = System.currentTimeMillis() + durationMinutes * 60_000L
        overrideDao.upsert(
            OverrideStateEntity(
                active = true,
                type = "SUPER_PASSWORD",
                untilMillis = until,
                activatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearOverride() {
        overrideDao.upsert(
            OverrideStateEntity(
                active = false,
                type = null,
                untilMillis = null,
                activatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun grantTemporaryUnlock(
        packageName: String,
        source: String,
        durationMinutes: Int
    ) {
        val until = System.currentTimeMillis() + durationMinutes * 60_000L
        unlockGrantDao.insert(
            UnlockGrantEntity(
                packageName = packageName,
                source = source,
                grantedUntilMillis = until,
                metadataJson = null
            )
        )
    }

    suspend fun isPackageTemporarilyUnlocked(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return unlockGrantDao.getActiveGrant(packageName, nowMillis) != null
    }
}
