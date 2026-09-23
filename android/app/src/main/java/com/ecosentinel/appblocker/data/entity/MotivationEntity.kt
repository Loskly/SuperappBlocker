package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "motivations")
data class MotivationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isDefault: Boolean
)
