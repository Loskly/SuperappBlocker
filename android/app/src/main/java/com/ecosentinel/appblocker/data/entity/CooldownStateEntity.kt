package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cooldown_state")
data class CooldownStateEntity(
    @PrimaryKey val ruleId: String,
    val blockedUntilMillis: Long,
    val triggeredAtMillis: Long
)
