package com.ecosentinel.appblocker.modules.friendpwd

import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType

class FriendPasswordModule : BlockModule {
    override val type = BlockModuleType.FRIEND_PASSWORD
    override val enabled = false
    override fun evaluate(context: BlockContext): BlockDecision? = null
}
