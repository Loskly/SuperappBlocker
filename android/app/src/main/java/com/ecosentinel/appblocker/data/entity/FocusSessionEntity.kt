package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ecosentinel.appblocker.focus.FocusMode

@Entity(tableName = "focus_session")
data class FocusSessionEntity(
    @PrimaryKey val id: Int = 1,
    val active: Boolean,
    val mode: FocusMode,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val blockedCategoryIdsJson: String,
    val blockedPackagesJson: String
)
