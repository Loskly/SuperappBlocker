package com.ecosentinel.appblocker.ui

import android.content.Context
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.tracker.UsageTracker
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

internal object BlockOverlayTexts {

    fun messageForReason(context: Context, reason: String): String {
        return when (reason) {
            BlockOverlayActivity.REASON_PERMANENT -> context.getString(R.string.block_message_permanent)
            BlockOverlayActivity.REASON_TIME_OF_DAY -> context.getString(R.string.block_message_time_of_day)
            BlockOverlayActivity.REASON_FOCUS -> context.getString(R.string.block_message_focus)
            BlockOverlayActivity.REASON_COOLDOWN -> context.getString(R.string.block_message_cooldown)
            BlockOverlayActivity.REASON_TIME_LIMIT -> context.getString(R.string.block_message_time_limit)
            BlockOverlayActivity.REASON_ADULT_URL -> context.getString(R.string.block_message_adult_url)
            BlockOverlayActivity.REASON_WEBSITE -> context.getString(R.string.block_message_website)
            BlockOverlayActivity.REASON_YOUTUBE_SHORTS -> context.getString(R.string.block_message_youtube_shorts)
            BlockOverlayActivity.REASON_INSTAGRAM_REELS -> context.getString(R.string.block_message_instagram_reels)
            else -> context.getString(R.string.block_message_time_limit)
        }
    }

    fun footerForReason(context: Context, packageName: String, reason: String): String {
        return when (reason) {
            BlockOverlayActivity.REASON_PERMANENT -> context.getString(R.string.mode_permanent)
            BlockOverlayActivity.REASON_FOCUS -> footerForFocus(context)
            BlockOverlayActivity.REASON_COOLDOWN -> footerForCooldown(context, packageName)
            BlockOverlayActivity.REASON_ADULT_URL -> context.getString(R.string.mode_adult_filter)
            BlockOverlayActivity.REASON_WEBSITE -> context.getString(R.string.mode_website_block)
            BlockOverlayActivity.REASON_YOUTUBE_SHORTS -> context.getString(R.string.mode_youtube_shorts_block)
            BlockOverlayActivity.REASON_INSTAGRAM_REELS -> context.getString(R.string.mode_instagram_reels_block)
            BlockOverlayActivity.REASON_TIME_OF_DAY -> {
                val schedule = findActiveRule(context, packageName, reason)?.scheduleJson?.let {
                    BlockSchedule.fromJson(it)
                }

                if (schedule == null) {
                    context.getString(R.string.mode_time_of_day)
                } else {
                    val remaining = BlockSchedule.formatDurationUntil(schedule.millisUntilBlockEnds())
                    context.getString(R.string.block_unlock_in, remaining)
                }
            }
            else -> {
                if (reason == BlockOverlayActivity.REASON_TIME_LIMIT) {
                    val tracker = UsageTracker(context)
                    val untilMidnight = tracker.millisUntilMidnight()
                    val hours = TimeUnit.MILLISECONDS.toHours(untilMidnight)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(untilMidnight) % 60
                    context.getString(R.string.block_reset_in, hours, minutes)
                } else {
                    context.getString(R.string.mode_time_limit)
                }
            }
        }
    }

    private fun footerForCooldown(context: Context, packageName: String): String {
        val cooldownManager = CooldownManager(context)
        val state = runBlocking { cooldownManager.findActiveBlockForPackage(packageName) }
            ?: return context.getString(R.string.mode_cooldown)
        val remaining = BlockSchedule.formatDurationUntil(
            runBlocking { cooldownManager.millisUntilUnblock(state.ruleId) }
        )
        return context.getString(R.string.block_cooldown_ends_in, remaining)
    }

    private fun footerForFocus(context: Context): String {
        val focusManager = FocusManager(context)
        val session = runBlocking { focusManager.getActiveSession() }
            ?: return context.getString(R.string.feature_focus_title)
        val remaining = BlockSchedule.formatDurationUntil(
            focusManager.millisUntilFocusEnds(session)
        )
        return context.getString(R.string.block_focus_ends_in, remaining)
    }

    private fun findActiveRule(
        context: Context,
        packageName: String,
        reason: String
    ): PolicyRuleEntity? {
        val blockMode = blockModeForReason(reason) ?: return null
        return runBlocking {
            AppGroupHelper.ensureCacheLoaded(context)
            val dao = AppDatabase.getInstance(context).policyRuleDao()
            val rules = dao.getEnabledRules().filter { it.moduleType == BlockModuleType.APP_LIMIT }
            rules.firstOrNull {
                it.targetType == TargetType.APP &&
                    it.packageName == packageName &&
                    it.blockMode == blockMode
            } ?: run {
                val groupRules = rules.filter {
                    it.targetType == TargetType.CUSTOM_GROUP &&
                        AppGroupHelper.belongsToGroup(packageName, it.featureId.orEmpty()) &&
                        it.blockMode == blockMode
                }
                groupRules.firstOrNull()
            } ?: run {
                val categoryId = AppCategoryHelper.categoryIdForPackage(context, packageName)
                rules.firstOrNull {
                    it.targetType == TargetType.CATEGORY &&
                        it.featureId == categoryId &&
                        it.blockMode == blockMode
                }
            }
        }
    }

    private fun blockModeForReason(reason: String): BlockMode? {
        return when (reason) {
            BlockOverlayActivity.REASON_PERMANENT -> BlockMode.PERMANENT
            BlockOverlayActivity.REASON_TIME_LIMIT -> BlockMode.TIME_LIMIT
            BlockOverlayActivity.REASON_TIME_OF_DAY -> BlockMode.TIME_OF_DAY
            BlockOverlayActivity.REASON_COOLDOWN -> BlockMode.COOLDOWN
            else -> null
        }
    }
}
