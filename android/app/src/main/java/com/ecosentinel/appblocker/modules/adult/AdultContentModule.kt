package com.ecosentinel.appblocker.modules.adult

import android.content.Context
import com.ecosentinel.appblocker.engine.BlockAction
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.ui.BlockOverlayActivity
import com.ecosentinel.appblocker.util.PermissionHelper

class AdultContentModule(context: Context) : BlockModule {

    private val appContext = context.applicationContext
    private val blocklistStore = UrlBlocklistStore(appContext)

    override val type = BlockModuleType.ADULT_CONTENT
    override val enabled = true

    override fun evaluate(context: BlockContext): BlockDecision? {
        if (!AdultFilterSettings.isEnabled(appContext)) {
            return null
        }
        if (!PermissionHelper.isAccessibilityServiceEnabled(appContext)) {
            return null
        }

        val packageName = context.foregroundPackage ?: return null
        if (!SupportedBrowsers.isBrowser(packageName)) {
            return null
        }

        val url = context.currentBrowserUrl
            ?: BrowserUrlState.currentUrl(packageName, context.nowMillis)
            ?: return null

        if (!blocklistStore.isBlocked(appContext, url)) {
            return null
        }

        return BlockDecision(
            action = BlockAction.BLOCK,
            reason = BlockOverlayActivity.REASON_ADULT_URL,
            moduleType = type
        )
    }
}
