package com.ecosentinel.appblocker.calories

import android.content.Context
import android.net.Uri
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.FoodEntryEntity
import com.ecosentinel.appblocker.tracker.DateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class CalorieTrackerRepository(private val appContext: Context) {

    private val dao = AppDatabase.getInstance(appContext).foodEntryDao()

    fun observeTodayEntries(): Flow<List<FoodEntryEntity>> {
        val bounds = todayBounds()
        return dao.observeForDay(bounds.first, bounds.second)
    }

    fun observeTodayTotalCalories(): Flow<Int> {
        val bounds = todayBounds()
        return dao.observeTotalCaloriesForDay(bounds.first, bounds.second)
    }

    suspend fun addEntry(
        name: String,
        calories: Int,
        photoPath: String = "",
        timestampMillis: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        dao.insert(
            FoodEntryEntity(
                timestamp = timestampMillis,
                name = name.trim(),
                calories = calories,
                photoPath = photoPath
            )
        )
    }

    suspend fun deleteEntry(id: Long) = withContext(Dispatchers.IO) {
        val entry = dao.getById(id)
        if (entry != null) {
            FoodPhotoStorage.deletePhoto(appContext, entry.photoPath)
        }
        dao.deleteById(id)
    }

    suspend fun getTotalCaloriesForToday(): Int = withContext(Dispatchers.IO) {
        val bounds = todayBounds()
        dao.sumCaloriesForDay(bounds.first, bounds.second)
    }

    suspend fun getRecentUniqueFoods(limit: Int = 5): List<FoodEntryEntity> = withContext(Dispatchers.IO) {
        val seen = LinkedHashSet<Pair<String, Int>>()
        val result = ArrayList<FoodEntryEntity>()
        for (entry in dao.getRecentEntries(RECENT_SCAN_LIMIT)) {
            val key = entry.name.lowercase(Locale.getDefault()) to entry.calories
            if (seen.add(key)) {
                result += entry
                if (result.size >= limit) {
                    break
                }
            }
        }
        result
    }

    fun createCameraPhotoFile(): File = FoodPhotoStorage.createPhotoFile(appContext)

    fun fileProviderUri(file: File): Uri = FoodPhotoStorage.fileProviderUri(appContext, file)

    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        FoodPhotoStorage.importFromUri(appContext, uri)
    }

    fun relativePathForFile(file: File): String = FoodPhotoStorage.relativePath(appContext, file)

    fun resolvePhotoFile(photoPath: String): File? = FoodPhotoStorage.resolveFile(appContext, photoPath)

    suspend fun deletePhotoByPath(photoPath: String) = withContext(Dispatchers.IO) {
        FoodPhotoStorage.deletePhoto(appContext, photoPath)
    }

    fun formatEntryTime(timestampMillis: Long): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestampMillis }
        return String.format(
            Locale.getDefault(),
            "%02d:%02d",
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE)
        )
    }

    fun formatEntryLine(entry: FoodEntryEntity): String {
        return "${entry.name} — ${entry.calories} ккал (${formatEntryTime(entry.timestamp)})"
    }

    private fun todayBounds(): Pair<Long, Long> {
        val todayKey = DateKeys.today()
        val start = DateKeys.startOfDayMillis(todayKey) ?: 0L
        val end = (DateKeys.endOfDayMillis(todayKey) ?: System.currentTimeMillis()) + 1L
        return start to end
    }

    companion object {
        private const val RECENT_SCAN_LIMIT = 50
    }
}
