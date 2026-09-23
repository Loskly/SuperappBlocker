package com.ecosentinel.appblocker.modules.inapp

import android.content.Context
import com.ecosentinel.appblocker.engine.BlockAction
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.util.PermissionHelper

class InAppFeatureModule(private val context: Context) : BlockModule {

    private val appContext = context.applicationContext

    override val type = BlockModuleType.IN_APP_FEATURE
    override val enabled = true

    override fun evaluate(blockContext: BlockContext): BlockDecision? {
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) {
            return null
        }

        val feature = blockContext.currentInAppFeature ?: return null
        val blocked = when (feature) {
            InAppFeature.YOUTUBE_SHORTS -> InAppFeatureSettings.isYoutubeShortsBlocked(appContext)
            InAppFeature.INSTAGRAM_REELS -> InAppFeatureSettings.isInstagramReelsBlocked(appContext)
            InAppFeature.BROWSER_INCOGNITO -> {
                val packageName = blockContext.foregroundPackage ?: return null
                InAppFeatureSettings.isBrowserIncognitoBlockedForPackage(appContext, packageName)
            }
        }
        if (!blocked) {
            return null
        }

        return BlockDecision(
            action = BlockAction.BLOCK,
            reason = feature.blockReason,
            moduleType = type
        )
    }
}
