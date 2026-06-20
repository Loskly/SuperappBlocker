package com.ecosentinel.appblocker.alarm

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SuperAlarmManager(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).superAlarmDao()

    fun observeAll() = dao.observeAll()

    suspend fun getById(id: Long): SuperAlarmEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun save(alarm: SuperAlarmEntity): Long = withContext(Dispatchers.IO) {
        val rowId = dao.upsert(alarm)
        val resolvedId = if (alarm.id != 0L) alarm.id else rowId
        val saved = dao.getById(resolvedId) ?: alarm.copy(id = resolvedId)
        AlarmScheduler.schedule(context, saved)
        resolvedId
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        val alarm = dao.getById(id) ?: return@withContext
        val updated = alarm.copy(enabled = enabled)
        dao.upsert(updated)
        AlarmScheduler.schedule(context, updated)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        AlarmScheduler.cancel(context, id)
        dao.deleteById(id)
    }

    suspend fun onAlarmDismissed(alarmId: Long) = withContext(Dispatchers.IO) {
        val alarm = dao.getById(alarmId) ?: return@withContext
        if (alarm.repeatDaysMask == 0) {
            val disabled = alarm.copy(enabled = false)
            dao.upsert(disabled)
            AlarmScheduler.cancel(context, alarmId)
        } else {
            AlarmScheduler.schedule(context, alarm)
        }
    }
}
