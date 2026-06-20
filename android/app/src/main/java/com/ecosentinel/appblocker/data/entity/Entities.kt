package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.TargetType

@Entity(tableName = "policy_rules")
data class PolicyRuleEntity(
    @PrimaryKey val id: String,
    val moduleType: BlockModuleType,
    val targetType: TargetType,
    val packageName: String?,
    val featureId: String?,
    val dailyLimitMinutes: Int?,
    val blockMode: BlockMode,
    val enabled: Boolean,
    val scheduleJson: String?,
    val metadataJson: String?
)

@Entity(tableName = "usage_daily")
data class UsageDailyEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val dateKey: String,
    val usedMillis: Long
)

@Entity(tableName = "unlock_grants")
data class UnlockGrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val source: String,
    val grantedUntilMillis: Long,
    val metadataJson: String?
)

@Entity(tableName = "override_state")
data class OverrideStateEntity(
    @PrimaryKey val id: Int = 1,
    val active: Boolean,
    val type: String?,
    val untilMillis: Long?,
    val activatedAtMillis: Long
)

@Entity(tableName = "password_protected_apps")
data class PasswordProtectedAppEntity(
    @PrimaryKey val packageName: String,
    val addedAtMillis: Long
)

@Entity(tableName = "app_groups")
data class AppGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long
)

@Entity(
    tableName = "app_group_members",
    primaryKeys = ["groupId", "packageName"]
)
data class AppGroupMemberEntity(
    val groupId: String,
    val packageName: String
)
