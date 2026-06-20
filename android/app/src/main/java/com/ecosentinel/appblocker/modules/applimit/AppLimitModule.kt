package com.ecosentinel.appblocker.modules.applimit

import android.content.Context
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.engine.BlockAction
import com.ecosentinel.appblocker.engine.BlockContext
import com.ecosentinel.appblocker.engine.BlockDecision
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.BlockModule
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.CooldownSettings
import com.ecosentinel.appblocker.engine.PolicyRuleIds
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.ui.BlockOverlayActivity
import kotlinx.coroutines.runBlocking

class AppLimitModule(private val context: Context) : BlockModule {

    override val type = BlockModuleType.APP_LIMIT
    override val enabled = true

    private val database = AppDatabase.getInstance(context)
    private val cooldownManager = CooldownManager(context)

    override fun evaluate(context: BlockContext): BlockDecision? {
        val packageName = context.foregroundPackage ?: return null
        val rules = runBlocking { database.policyRuleDao().getEnabledRules() }
            .filter { it.moduleType == BlockModuleType.APP_LIMIT }

        val appRules = rules.filter { it.targetType == TargetType.APP && it.packageName == packageName }
        evaluateRules(appRules, packageName, context) { _ ->
            context.usageMillisToday[packageName] ?: 0L
        }?.let { return it }

        val matchingGroupRules = rules.filter { rule ->
            rule.targetType == TargetType.CUSTOM_GROUP &&
                AppGroupHelper.belongsToGroup(packageName, rule.featureId.orEmpty())
        }
        if (matchingGroupRules.isNotEmpty()) {
            evaluateRules(matchingGroupRules, packageName, context) { rule ->
                AppGroupHelper.aggregateUsageForGroup(
                    context.usageMillisToday,
                    rule.featureId.orEmpty(),
                    this.context.packageName
                )
            }?.let { return it }
        }

        val categoryId = AppCategoryHelper.categoryIdForPackage(this.context, packageName)
        val categoryRules = rules.filter {
            it.targetType == TargetType.CATEGORY && it.featureId == categoryId
        }
        return evaluateRules(categoryRules, packageName, context) { _ ->
            AppCategoryHelper.aggregateUsageForCategory(
                this.context,
                context.usageMillisToday,
                categoryId
            )
        }
    }

    private fun evaluateRules(
        rules: List<PolicyRuleEntity>,
        packageName: String,
        blockContext: BlockContext,
        usedMillisProvider: (PolicyRuleEntity) -> Long
    ): BlockDecision? {
        for (rule in rules) {
            when (rule.blockMode) {
                BlockMode.PERMANENT -> {
                    return BlockDecision(
                        action = BlockAction.BLOCK,
                        reason = BlockOverlayActivity.REASON_PERMANENT,
                        moduleType = type
                    )
                }
                BlockMode.TIME_LIMIT -> {
                    val limitMinutes = rule.dailyLimitMinutes ?: continue
                    val usedMillis = usedMillisProvider(rule)
                    if (usedMillis >= limitMinutes * 60_000L) {
                        return BlockDecision(
                            action = BlockAction.BLOCK,
                            reason = BlockOverlayActivity.REASON_TIME_LIMIT,
                            moduleType = type
                        )
                    }
                }
                BlockMode.TIME_OF_DAY -> {
                    val schedule = BlockSchedule.fromJson(rule.scheduleJson) ?: continue
                    if (schedule.isActiveNow()) {
                        return BlockDecision(
                            action = BlockAction.BLOCK,
                            reason = BlockOverlayActivity.REASON_TIME_OF_DAY,
                            moduleType = type
                        )
                    }
                }
                BlockMode.COOLDOWN -> {
                    if (CooldownSettings.fromJson(rule.metadataJson) == null) {
                        continue
                    }
                    val blocked = runBlocking {
                        cooldownManager.isBlocked(rule.id, blockContext.nowMillis)
                    }
                    if (blocked) {
                        return BlockDecision(
                            action = BlockAction.BLOCK,
                            reason = BlockOverlayActivity.REASON_COOLDOWN,
                            moduleType = type
                        )
                    }
                }
            }
        }
        return null
    }

    companion object {
        fun createRule(
            packageName: String,
            blockMode: BlockMode,
            dailyLimitMinutes: Int? = null,
            schedule: BlockSchedule? = null,
            cooldownSettings: CooldownSettings? = null,
            enabled: Boolean = true
        ): PolicyRuleEntity {
            return PolicyRuleEntity(
                id = PolicyRuleIds.forApp(packageName, blockMode),
                moduleType = BlockModuleType.APP_LIMIT,
                targetType = TargetType.APP,
                packageName = packageName,
                featureId = null,
                dailyLimitMinutes = if (blockMode == BlockMode.TIME_LIMIT) dailyLimitMinutes else null,
                blockMode = blockMode,
                enabled = enabled,
                scheduleJson = if (blockMode == BlockMode.TIME_OF_DAY) schedule?.toJson() else null,
                metadataJson = if (blockMode == BlockMode.COOLDOWN) cooldownSettings?.toJson() else null
            )
        }

        fun createCategoryRule(
            categoryId: String,
            blockMode: BlockMode,
            dailyLimitMinutes: Int? = null,
            schedule: BlockSchedule? = null,
            cooldownSettings: CooldownSettings? = null,
            enabled: Boolean = true
        ): PolicyRuleEntity {
            return PolicyRuleEntity(
                id = PolicyRuleIds.forCategory(categoryId, blockMode),
                moduleType = BlockModuleType.APP_LIMIT,
                targetType = TargetType.CATEGORY,
                packageName = null,
                featureId = categoryId,
                dailyLimitMinutes = if (blockMode == BlockMode.TIME_LIMIT) dailyLimitMinutes else null,
                blockMode = blockMode,
                enabled = enabled,
                scheduleJson = if (blockMode == BlockMode.TIME_OF_DAY) schedule?.toJson() else null,
                metadataJson = if (blockMode == BlockMode.COOLDOWN) cooldownSettings?.toJson() else null
            )
        }

        fun createGroupRule(
            groupId: String,
            blockMode: BlockMode,
            dailyLimitMinutes: Int? = null,
            schedule: BlockSchedule? = null,
            cooldownSettings: CooldownSettings? = null,
            enabled: Boolean = true
        ): PolicyRuleEntity {
            return PolicyRuleEntity(
                id = PolicyRuleIds.forCustomGroup(groupId, blockMode),
                moduleType = BlockModuleType.APP_LIMIT,
                targetType = TargetType.CUSTOM_GROUP,
                packageName = null,
                featureId = groupId,
                dailyLimitMinutes = if (blockMode == BlockMode.TIME_LIMIT) dailyLimitMinutes else null,
                blockMode = blockMode,
                enabled = enabled,
                scheduleJson = if (blockMode == BlockMode.TIME_OF_DAY) schedule?.toJson() else null,
                metadataJson = if (blockMode == BlockMode.COOLDOWN) cooldownSettings?.toJson() else null
            )
        }

        fun createWebsiteRule(
            domain: String,
            blockMode: BlockMode,
            schedule: BlockSchedule? = null,
            enabled: Boolean = true
        ): PolicyRuleEntity {
            return PolicyRuleEntity(
                id = PolicyRuleIds.forWebsite(domain, blockMode),
                moduleType = BlockModuleType.APP_LIMIT,
                targetType = TargetType.URL_PATTERN,
                packageName = null,
                featureId = domain,
                dailyLimitMinutes = null,
                blockMode = blockMode,
                enabled = enabled,
                scheduleJson = if (blockMode == BlockMode.TIME_OF_DAY) schedule?.toJson() else null,
                metadataJson = null
            )
        }
    }
}
