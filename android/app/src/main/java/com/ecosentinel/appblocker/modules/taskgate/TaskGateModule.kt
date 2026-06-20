package com.ecosentinel.appblocker.modules.taskgate

import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType

class TaskGateModule : BlockModule {
    override val type = BlockModuleType.TASK_GATE
    override val enabled = false
    override fun evaluate(context: BlockContext): BlockDecision? = null
}
