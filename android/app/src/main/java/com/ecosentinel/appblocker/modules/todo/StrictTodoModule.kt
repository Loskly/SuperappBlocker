package com.ecosentinel.appblocker.modules.todo

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.engine.BlockAction
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.ui.BlockOverlayActivity
import kotlinx.coroutines.runBlocking

class StrictTodoModule(context: Context) : BlockModule {
    override val type = BlockModuleType.TASK_GATE // Or create a new one, but TASK_GATE is similar
    override val enabled = true

    private val database = AppDatabase.getInstance(context)

    override fun evaluate(context: BlockContext): BlockDecision? {
        val packageName = context.foregroundPackage ?: return null

        val strictTodos = runBlocking {
            database.todoDao().getStrictIncompleteTodos()
        }

        if (strictTodos.isNotEmpty()) {
            return BlockDecision(
                action = BlockAction.REQUIRE_TASK,
                reason = "TODO_STRICT_BLOCK",
                moduleType = type
            )
        }

        return null
    }
}
