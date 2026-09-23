package com.ecosentinel.appblocker.survival

import android.content.Context
import com.ecosentinel.appblocker.util.PermissionHelper

enum class SurvivalAccessibilityRequirementMode {
    NOTIFICATION,
    OVERLAY;

    companion object {
        fun fromStorage(value: String?): SurvivalAccessibilityRequirementMode {
            return values().firstOrNull { it.name == value } ?: NOTIFICATION
        }
    }
}

object SurvivalSettings {

    private const val PREFS_NAME = "survival_mode_settings"
    private const val KEY_REQUIRE_ACCESSIBILITY = "require_accessibility"
    private const val KEY_ACCESSIBILITY_REQUIREMENT_MODE = "accessibility_requirement_mode"

    fun isAccessibilityRequirementEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_REQUIRE_ACCESSIBILITY, true)
    }

    fun setAccessibilityRequirementEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_REQUIRE_ACCESSIBILITY, enabled)
            .apply()
    }

    fun getAccessibilityRequirementMode(context: Context): SurvivalAccessibilityRequirementMode {
        return SurvivalAccessibilityRequirementMode.fromStorage(
            prefs(context).getString(KEY_ACCESSIBILITY_REQUIREMENT_MODE, null)
        )
    }

    fun setAccessibilityRequirementMode(
        context: Context,
        mode: SurvivalAccessibilityRequirementMode
    ) {
        prefs(context).edit()
            .putString(KEY_ACCESSIBILITY_REQUIREMENT_MODE, mode.name)
            .apply()
    }

    fun requiredIssues(context: Context, snapshot: AppHealthSnapshot): List<AppHealthIssue> {
        val accessibilityRequired = isAccessibilityRequirementEnabled(context)
        return snapshot.issues.filterNot { issue ->
            issue == AppHealthIssue.ACCESSIBILITY_MISSING && !accessibilityRequired
        }
    }

    fun notificationIssues(context: Context, snapshot: AppHealthSnapshot): List<AppHealthIssue> {
        val mode = getAccessibilityRequirementMode(context)
        val overlayCanBeShown = PermissionHelper.canDrawOverlays(context)
        return requiredIssues(context, snapshot).filterNot { issue ->
            issue == AppHealthIssue.ACCESSIBILITY_MISSING &&
                mode == SurvivalAccessibilityRequirementMode.OVERLAY &&
                overlayCanBeShown
        }
    }

    fun shouldShowAccessibilityOverlay(context: Context, snapshot: AppHealthSnapshot): Boolean {
        return isAccessibilityRequirementEnabled(context) &&
            getAccessibilityRequirementMode(context) == SurvivalAccessibilityRequirementMode.OVERLAY &&
            !snapshot.accessibilityEnabled
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
