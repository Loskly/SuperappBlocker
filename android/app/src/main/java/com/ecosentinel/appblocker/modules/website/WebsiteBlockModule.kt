package com.ecosentinel.appblocker.modules.website

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.engine.BlockAction
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.modules.adult.AdultFilterSettings
import com.ecosentinel.appblocker.modules.adult.BrowserUrlState
import com.ecosentinel.appblocker.modules.adult.SupportedBrowsers
import com.ecosentinel.appblocker.modules.adult.UrlBlocklistStore
import com.ecosentinel.appblocker.ui.BlockOverlayActivity
import com.ecosentinel.appblocker.util.PermissionHelper
import kotlinx.coroutines.runBlocking

class WebsiteBlockModule(private val context: Context) : BlockModule {

    private val database = AppDatabase.getInstance(context)

    override val type = BlockModuleType.APP_LIMIT
    override val enabled = true

    override fun evaluate(blockContext: BlockContext): BlockDecision? {
        if (!PermissionHelper.isAccessibilityServiceEnabled(context)) {
            return null
        }

        val packageName = blockContext.foregroundPackage ?: return null
        if (!SupportedBrowsers.isBrowser(packageName)) {
            return null
        }

        val url = blockContext.currentBrowserUrl
            ?: BrowserUrlState.currentUrl(packageName, blockContext.nowMillis)
            ?: return null

        val host = UrlBlocklistStore.extractHost(url) ?: return null

        val rules = runBlocking {
            database.policyRuleDao().getEnabledRules()
                .filter { it.moduleType == BlockModuleType.APP_LIMIT && it.targetType == TargetType.URL_PATTERN }
        }

        for (rule in rules) {
            val domain = rule.featureId?.let { AdultFilterSettings.normalizeDomain(it) } ?: continue
            if (!UrlBlocklistStore.hostMatches(host, domain)) {
                continue
            }
            evaluateRule(rule)?.let { return it }
        }
        return null
    }

    private fun evaluateRule(rule: PolicyRuleEntity): BlockDecision? {
        return when (rule.blockMode) {
            BlockMode.PERMANENT -> BlockDecision(
                action = BlockAction.BLOCK,
                reason = BlockOverlayActivity.REASON_WEBSITE,
                moduleType = type
            )
            BlockMode.TIME_OF_DAY -> {
                val schedule = BlockSchedule.fromJson(rule.scheduleJson) ?: return null
                if (schedule.isActiveNow()) {
                    BlockDecision(
                        action = BlockAction.BLOCK,
                        reason = BlockOverlayActivity.REASON_WEBSITE,
                        moduleType = type
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
