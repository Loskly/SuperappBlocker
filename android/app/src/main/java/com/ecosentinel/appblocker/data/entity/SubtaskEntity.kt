package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "todo_subtasks",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["todoId"])]
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val todoId: Long,
    val title: String,
    val completed: Boolean = false
)
