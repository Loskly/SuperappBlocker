package com.ecosentinel.appblocker.engine

import android.content.Context
import com.ecosentinel.appblocker.BuildConfig
import com.ecosentinel.appblocker.modules.apppassword.AppPasswordModule
import kotlinx.coroutines.runBlocking

class PolicyEngine(context: Context) {

    private val registry = BlockModuleRegistry(context)
    private val overrideManager = OverrideManager(context)
    private val appPasswordModule = AppPasswordModule(context)

    private val alwaysAllowed = setOf(
        BuildConfig.APPLICATION_ID,
        "com.android.systemui",
        "com.android.settings"
    )

    fun shouldBlock(context: BlockContext): BlockDecision? {
        val packageName = context.foregroundPackage ?: return null
        if (packageName in alwaysAllowed) {
            return null
        }

        if (overrideManager.isOverrideActive(context.nowMillis)) {
            return null
        }

        if (runBlocking { overrideManager.isPackageTemporarilyUnlocked(packageName, context.nowMillis) }) {
            return null
        }

        for (module in registry.enabledModules()) {
            val decision = module.evaluate(context) ?: continue
            if (decision.action != BlockAction.ALLOW) {
                return decision
            }
        }
        return null
    }

    fun shouldRequirePassword(context: BlockContext): Boolean {
        return appPasswordModule.shouldRequirePassword(context.foregroundPackage)
    }
}
