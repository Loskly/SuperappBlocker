package com.ecosentinel.appblocker.service

import android.content.Context
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import java.util.concurrent.TimeUnit

class LimitWarningChecker(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val warningStore = LimitWarningStore(appContext)
    private val usageTracker = UsageTracker(appContext)

    suspend fun checkAndNotify(usageByPackage: Map<String, Long>) {
        val dateKey = usageTracker.todayKey()
        warningStore.clearExceptDate(dateKey)

        val rules = database.policyRuleDao().getEnabledRules()
            .filter { it.moduleType == BlockModuleType.APP_LIMIT && it.blockMode == BlockMode.TIME_LIMIT }

        for (rule in rules) {
            val limitMinutes = rule.dailyLimitMinutes ?: continue
            if (limitMinutes <= 0) {
                continue
            }
            if (warningStore.wasWarned(dateKey, rule.id)) {
                continue
            }

            val limitMillis = limitMinutes * 60_000L
            val usedMillis = when (rule.targetType) {
                TargetType.APP -> {
                    val packageName = rule.packageName ?: continue
                    usageByPackage[packageName] ?: 0L
                }
                TargetType.CATEGORY -> {
                    val categoryId = rule.featureId ?: continue
                    AppCategoryHelper.aggregateUsageForCategory(appContext, usageByPackage, categoryId)
                }
                TargetType.CUSTOM_GROUP -> {
                    val groupId = rule.featureId ?: continue
                    AppGroupHelper.aggregateUsageForGroup(usageByPackage, groupId)
                }
                else -> continue
            }

            val remainingMillis = limitMillis - usedMillis
            if (remainingMillis <= 0L || remainingMillis > WARNING_BEFORE_MS) {
                continue
            }

            val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis).coerceAtLeast(1L)

            when (rule.targetType) {
                TargetType.APP -> {
                    val packageName = rule.packageName ?: continue
                    val label = InstalledAppsHelper.getAppLabel(appContext, packageName)
                    LimitWarningNotificationHelper.showAppWarning(
                        context = appContext,
                        ruleId = rule.id,
                        appLabel = label,
                        remainingMinutes = remainingMinutes,
                        limitMinutes = limitMinutes
                    )
                }
                TargetType.CATEGORY -> {
                    val categoryId = rule.featureId ?: continue
                    val categoryName = AppCategoryHelper.displayNameForCategoryId(categoryId)
                    LimitWarningNotificationHelper.showCategoryWarning(
                        context = appContext,
                        ruleId = rule.id,
                        categoryName = categoryName,
                        remainingMinutes = remainingMinutes,
                        limitMinutes = limitMinutes
                    )
                }
                TargetType.CUSTOM_GROUP -> {
                    val groupId = rule.featureId ?: continue
                    val groupName = AppGroupHelper.displayNameForGroupId(groupId)
                    LimitWarningNotificationHelper.showGroupWarning(
                        context = appContext,
                        ruleId = rule.id,
                        groupName = groupName,
                        remainingMinutes = remainingMinutes,
                        limitMinutes = limitMinutes
                    )
                }
                else -> continue
            }

            warningStore.markWarned(dateKey, rule.id)
        }
    }

    companion object {
        private val WARNING_BEFORE_MS = TimeUnit.MINUTES.toMillis(5)
    }
}
