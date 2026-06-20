package com.ecosentinel.appblocker.cooldown

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.CooldownStateEntity
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.CooldownSettings
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.tracker.UsageTracker

class CooldownManager(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val cooldownDao = database.cooldownStateDao()
    private val usageTracker = UsageTracker(appContext)

    suspend fun isBlocked(ruleId: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        cleanupExpired(nowMillis)
        val state = cooldownDao.getByRuleId(ruleId) ?: return false
        return state.blockedUntilMillis > nowMillis
    }

    suspend fun millisUntilUnblock(ruleId: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val state = cooldownDao.getByRuleId(ruleId) ?: return 0L
        return (state.blockedUntilMillis - nowMillis).coerceAtLeast(0L)
    }

    suspend fun findActiveBlockForPackage(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): CooldownStateEntity? {
        cleanupExpired(nowMillis)
        val rules = database.policyRuleDao().getEnabledRules()
            .filter { it.moduleType == BlockModuleType.APP_LIMIT && it.blockMode == BlockMode.COOLDOWN }

        for (rule in rules) {
            val matches = when (rule.targetType) {
                TargetType.APP -> rule.packageName == packageName
                TargetType.CATEGORY -> AppCategoryHelper.belongsToCategory(
                    appContext,
                    packageName,
                    rule.featureId.orEmpty()
                )
                TargetType.CUSTOM_GROUP -> AppGroupHelper.belongsToGroup(
                    packageName,
                    rule.featureId.orEmpty()
                )
                else -> false
            }
            if (!matches) {
                continue
            }
            val state = cooldownDao.getByRuleId(rule.id) ?: continue
            if (state.blockedUntilMillis > nowMillis) {
                return state
            }
        }
        return null
    }

    suspend fun updateCooldownStates(nowMillis: Long = System.currentTimeMillis()) {
        cleanupExpired(nowMillis)

        val rules = database.policyRuleDao().getEnabledRules()
            .filter { it.moduleType == BlockModuleType.APP_LIMIT && it.blockMode == BlockMode.COOLDOWN }

        for (rule in rules) {
            val settings = CooldownSettings.fromJson(rule.metadataJson) ?: continue
            if (!settings.isValid()) {
                continue
            }

            val existing = cooldownDao.getByRuleId(rule.id)
            if (existing != null && existing.blockedUntilMillis > nowMillis) {
                continue
            }

            val usedMillis = usageMillisForRule(rule, settings.windowMinutes, nowMillis)
            val thresholdMillis = settings.usageMinutes * 60_000L
            if (usedMillis < thresholdMillis) {
                continue
            }

            cooldownDao.upsert(
                CooldownStateEntity(
                    ruleId = rule.id,
                    blockedUntilMillis = nowMillis + settings.blockMinutes * 60_000L,
                    triggeredAtMillis = nowMillis
                )
            )
        }
    }

    suspend fun clearForRule(ruleId: String) {
        cooldownDao.deleteByRuleId(ruleId)
    }

    private suspend fun cleanupExpired(nowMillis: Long) {
        cooldownDao.deleteExpired(nowMillis)
    }

    private fun usageMillisForRule(
        rule: PolicyRuleEntity,
        windowMinutes: Int,
        nowMillis: Long
    ): Long {
        return when (rule.targetType) {
            TargetType.APP -> {
                val packageName = rule.packageName ?: return 0L
                usageTracker.getPackageUsageInWindow(packageName, windowMinutes, nowMillis)
            }
            TargetType.CATEGORY -> {
                val categoryId = rule.featureId ?: return 0L
                usageTracker.getCategoryUsageInWindow(categoryId, windowMinutes, nowMillis)
            }
            TargetType.CUSTOM_GROUP -> {
                val groupId = rule.featureId ?: return 0L
                usageTracker.getGroupUsageInWindow(groupId, windowMinutes, nowMillis)
            }
            else -> 0L
        }
    }
}
