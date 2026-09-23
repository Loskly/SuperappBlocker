package com.ecosentinel.appblocker.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class TodoWithSubtasks(
    @Embedded val todo: TodoEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "todoId"
    )
    val subtasks: List<SubtaskEntity>
)
