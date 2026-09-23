package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_items")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    /** 0 means no due date/time. */
    val dueAtMillis: Long = 0L,
    val completed: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long = 0L,
    val priority: Int = 0,
    val tags: String = "",
    val recurrenceRule: String = "",
    val unlockAppPackage: String = "",
    val rewardMinutes: Int = 0,
    val isStrictBlock: Boolean = false
)
