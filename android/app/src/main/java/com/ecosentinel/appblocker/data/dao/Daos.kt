package com.ecosentinel.appblocker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ecosentinel.appblocker.data.entity.AppGroupEntity
import com.ecosentinel.appblocker.data.entity.AppGroupMemberEntity
import com.ecosentinel.appblocker.data.entity.FoodEntryEntity
import com.ecosentinel.appblocker.data.entity.FocusSessionEntity
import com.ecosentinel.appblocker.data.entity.CooldownStateEntity
import com.ecosentinel.appblocker.data.entity.OverrideStateEntity
import com.ecosentinel.appblocker.data.entity.PasswordProtectedAppEntity
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.data.entity.TodoEntity
import com.ecosentinel.appblocker.data.entity.UnlockGrantEntity
import com.ecosentinel.appblocker.data.entity.UsageDailyEntity
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import kotlinx.coroutines.flow.Flow

data class DailyTotalEntity(
    val dateKey: String,
    val totalMillis: Long
)

data class PackageUsageTotal(
    val packageName: String,
    val usedMillis: Long
)

@Dao
interface PolicyRuleDao {
    @Query("SELECT * FROM policy_rules")
    suspend fun getAllRules(): List<PolicyRuleEntity>

    @Query("SELECT * FROM policy_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<PolicyRuleEntity>

    @Query("SELECT * FROM policy_rules")
    fun observeAll(): Flow<List<PolicyRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<PolicyRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: PolicyRuleEntity)

    @Query("DELETE FROM policy_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM policy_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): PolicyRuleEntity?

    @Query("SELECT * FROM policy_rules WHERE packageName = :packageName")
    suspend fun getAllByPackageName(packageName: String): List<PolicyRuleEntity>

    @Query(
        "SELECT * FROM policy_rules WHERE packageName = :packageName AND blockMode = :blockMode LIMIT 1"
    )
    suspend fun getByPackageNameAndMode(packageName: String, blockMode: BlockMode): PolicyRuleEntity?

    @Query("SELECT * FROM policy_rules WHERE featureId = :categoryId AND targetType = 'CATEGORY' LIMIT 1")
    suspend fun getByCategoryId(categoryId: String): PolicyRuleEntity?

    @Query("SELECT * FROM policy_rules WHERE featureId = :categoryId AND targetType = 'CATEGORY'")
    suspend fun getAllByCategoryId(categoryId: String): List<PolicyRuleEntity>

    @Query(
        "SELECT * FROM policy_rules WHERE featureId = :categoryId AND targetType = 'CATEGORY' " +
            "AND blockMode = :blockMode LIMIT 1"
    )
    suspend fun getByCategoryIdAndMode(categoryId: String, blockMode: BlockMode): PolicyRuleEntity?

    @Query("SELECT * FROM policy_rules WHERE featureId = :groupId AND targetType = 'CUSTOM_GROUP' LIMIT 1")
    suspend fun getByGroupId(groupId: String): PolicyRuleEntity?

    @Query("SELECT * FROM policy_rules WHERE featureId = :groupId AND targetType = 'CUSTOM_GROUP'")
    suspend fun getAllByGroupId(groupId: String): List<PolicyRuleEntity>

    @Query(
        "SELECT * FROM policy_rules WHERE featureId = :groupId AND targetType = 'CUSTOM_GROUP' " +
            "AND blockMode = :blockMode LIMIT 1"
    )
    suspend fun getByGroupIdAndMode(groupId: String, blockMode: BlockMode): PolicyRuleEntity?

    @Query(
        "SELECT * FROM policy_rules WHERE featureId = :domain AND targetType = 'URL_PATTERN' " +
            "AND blockMode = :blockMode LIMIT 1"
    )
    suspend fun getByWebsiteDomainAndMode(domain: String, blockMode: BlockMode): PolicyRuleEntity?

    @Query("SELECT * FROM policy_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PolicyRuleEntity?

    @Query("DELETE FROM policy_rules")
    suspend fun deleteAll()
}

@Dao
interface AppGroupDao {
    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AppGroupEntity>>

    @Query("SELECT * FROM app_groups ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<AppGroupEntity>

    @Query("SELECT * FROM app_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AppGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: AppGroupEntity)

    @Query("DELETE FROM app_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT packageName FROM app_group_members WHERE groupId = :groupId")
    suspend fun getMemberPackages(groupId: String): List<String>

    @Query("SELECT COUNT(*) FROM app_group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: String): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM app_group_members WHERE groupId = :groupId AND packageName = :packageName)"
    )
    suspend fun isMember(groupId: String, packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<AppGroupMemberEntity>)

    @Query("DELETE FROM app_group_members WHERE groupId = :groupId")
    suspend fun deleteMembersForGroup(groupId: String)
}

@Dao
interface UsageDailyDao {
    @Query("SELECT * FROM usage_daily WHERE dateKey = :dateKey")
    suspend fun getForDate(dateKey: String): List<UsageDailyEntity>

    @Query("DELETE FROM usage_daily WHERE dateKey = :dateKey")
    suspend fun deleteForDate(dateKey: String)

    @Query("SELECT * FROM usage_daily WHERE dateKey = :dateKey")
    fun observeForDate(dateKey: String): Flow<List<UsageDailyEntity>>

    @Query("SELECT dateKey, SUM(usedMillis) AS totalMillis FROM usage_daily WHERE dateKey >= :fromDateKey GROUP BY dateKey ORDER BY dateKey ASC")
    suspend fun getDailyTotalsSince(fromDateKey: String): List<DailyTotalEntity>

    @Query(
        "SELECT packageName, SUM(usedMillis) AS usedMillis FROM usage_daily " +
            "WHERE dateKey >= :fromDateKey AND dateKey <= :toDateKey " +
            "GROUP BY packageName ORDER BY usedMillis DESC"
    )
    suspend fun getPackageTotalsBetween(fromDateKey: String, toDateKey: String): List<PackageUsageTotal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UsageDailyEntity)
}

@Dao
interface UnlockGrantDao {
    @Query(
        "SELECT * FROM unlock_grants WHERE packageName = :packageName " +
            "AND grantedUntilMillis > :nowMillis LIMIT 1"
    )
    suspend fun getActiveGrant(packageName: String, nowMillis: Long): UnlockGrantEntity?

    @Insert
    suspend fun insert(grant: UnlockGrantEntity)
}

@Dao
interface OverrideStateDao {
    @Query("SELECT * FROM override_state WHERE id = 1 LIMIT 1")
    suspend fun getState(): OverrideStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: OverrideStateEntity)
}

@Dao
interface CooldownStateDao {
    @Query("SELECT * FROM cooldown_state WHERE ruleId = :ruleId LIMIT 1")
    suspend fun getByRuleId(ruleId: String): CooldownStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: CooldownStateEntity)

    @Query("DELETE FROM cooldown_state WHERE ruleId = :ruleId")
    suspend fun deleteByRuleId(ruleId: String)

    @Query("DELETE FROM cooldown_state WHERE blockedUntilMillis <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query("SELECT ruleId FROM cooldown_state WHERE blockedUntilMillis > :nowMillis")
    suspend fun getActiveRuleIds(nowMillis: Long): List<String>
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_session WHERE id = 1 LIMIT 1")
    suspend fun getSession(): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: FocusSessionEntity)
}

@Dao
interface SuperAlarmDao {
    @Query("SELECT * FROM super_alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<SuperAlarmEntity>>

    @Query("SELECT * FROM super_alarms ORDER BY hour, minute")
    suspend fun getAll(): List<SuperAlarmEntity>

    @Query("SELECT * FROM super_alarms WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SuperAlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: SuperAlarmEntity): Long

    @Query("DELETE FROM super_alarms WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TodoDao {
    @Query(
        """
        SELECT * FROM todo_items
        ORDER BY completed ASC,
            CASE WHEN dueAtMillis = 0 THEN 1 ELSE 0 END ASC,
            dueAtMillis ASC,
            createdAtMillis DESC
        """
    )
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TodoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity): Long

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FoodEntryDao {
    @Query(
        """
        SELECT * FROM food_entries
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis
        ORDER BY timestamp DESC
        """
    )
    fun observeForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<FoodEntryEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(calories), 0) FROM food_entries
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis
        """
    )
    fun observeTotalCaloriesForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(calories), 0) FROM food_entries
        WHERE timestamp >= :dayStartMillis AND timestamp < :dayEndMillis
        """
    )
    suspend fun sumCaloriesForDay(dayStartMillis: Long, dayEndMillis: Long): Int

    @Query("SELECT * FROM food_entries ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEntries(limit: Int): List<FoodEntryEntity>

    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("SELECT * FROM food_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FoodEntryEntity?

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PasswordProtectedAppDao {
    @Query("SELECT * FROM password_protected_apps ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<PasswordProtectedAppEntity>>

    @Query("SELECT * FROM password_protected_apps ORDER BY addedAtMillis DESC")
    suspend fun getAll(): List<PasswordProtectedAppEntity>

    @Query("SELECT COUNT(*) FROM password_protected_apps WHERE packageName = :packageName")
    suspend fun isProtected(packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PasswordProtectedAppEntity)

    @Query("DELETE FROM password_protected_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
