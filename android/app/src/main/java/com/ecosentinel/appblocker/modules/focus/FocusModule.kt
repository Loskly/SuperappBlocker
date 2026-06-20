package com.ecosentinel.appblocker.modules.focus

import android.content.Context
import com.ecosentinel.appblocker.engine.BlockAction
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.ui.BlockOverlayActivity
import kotlinx.coroutines.runBlocking

class FocusModule(context: Context) : BlockModule {

    override val type = BlockModuleType.FOCUS
    override val enabled = true

    private val appContext = context.applicationContext
    private val focusManager = FocusManager(appContext)

    override fun evaluate(context: BlockContext): BlockDecision? {
        val packageName = context.foregroundPackage ?: return null
        val session = runBlocking { focusManager.getActiveSession(context.nowMillis) } ?: return null
        if (!focusManager.isPackageBlocked(appContext, packageName, session)) {
            return null
        }
        return BlockDecision(
            action = BlockAction.BLOCK,
            reason = BlockOverlayActivity.REASON_FOCUS,
            moduleType = type
        )
    }
}
