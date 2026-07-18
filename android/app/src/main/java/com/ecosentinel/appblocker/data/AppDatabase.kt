package com.ecosentinel.appblocker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ecosentinel.appblocker.data.dao.AppGroupDao
import com.ecosentinel.appblocker.data.dao.CooldownStateDao
import com.ecosentinel.appblocker.data.dao.FocusSessionDao
import com.ecosentinel.appblocker.data.dao.FoodEntryDao
import com.ecosentinel.appblocker.data.dao.OverrideStateDao
import com.ecosentinel.appblocker.data.dao.PasswordProtectedAppDao
import com.ecosentinel.appblocker.data.dao.PolicyRuleDao
import com.ecosentinel.appblocker.data.dao.UnlockGrantDao
import com.ecosentinel.appblocker.data.dao.SuperAlarmDao
import com.ecosentinel.appblocker.data.dao.TodoDao
import com.ecosentinel.appblocker.data.dao.UsageDailyDao
import com.ecosentinel.appblocker.data.entity.AppGroupEntity
import com.ecosentinel.appblocker.data.entity.AppGroupMemberEntity
import com.ecosentinel.appblocker.data.entity.CooldownStateEntity
import com.ecosentinel.appblocker.data.entity.FocusSessionEntity
import com.ecosentinel.appblocker.data.entity.FoodEntryEntity
import com.ecosentinel.appblocker.data.entity.OverrideStateEntity
import com.ecosentinel.appblocker.data.entity.PasswordProtectedAppEntity
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.data.entity.UnlockGrantEntity
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.data.entity.TodoEntity
import com.ecosentinel.appblocker.data.entity.UsageDailyEntity

@Database(
    entities = [
        PolicyRuleEntity::class,
        UsageDailyEntity::class,
        UnlockGrantEntity::class,
        OverrideStateEntity::class,
        PasswordProtectedAppEntity::class,
        FocusSessionEntity::class,
        CooldownStateEntity::class,
        SuperAlarmEntity::class,
        AppGroupEntity::class,
        AppGroupMemberEntity::class,
        TodoEntity::class,
        FoodEntryEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun policyRuleDao(): PolicyRuleDao
    abstract fun usageDailyDao(): UsageDailyDao
    abstract fun unlockGrantDao(): UnlockGrantDao
    abstract fun overrideStateDao(): OverrideStateDao
    abstract fun passwordProtectedAppDao(): PasswordProtectedAppDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun cooldownStateDao(): CooldownStateDao
    abstract fun superAlarmDao(): SuperAlarmDao
    abstract fun appGroupDao(): AppGroupDao
    abstract fun todoDao(): TodoDao
    abstract fun foodEntryDao(): FoodEntryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appblocker.db"
                )
                    .addMigrations(*DatabaseMigrations.ALL)
                    .build().also { instance = it }
            }
        }
    }
}
