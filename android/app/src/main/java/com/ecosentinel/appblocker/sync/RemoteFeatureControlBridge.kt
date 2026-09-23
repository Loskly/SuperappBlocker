package com.ecosentinel.appblocker.sync

import android.content.Context
import com.ecosentinel.appblocker.focus.FocusConfigStore
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.focus.FocusMode
import com.ecosentinel.appblocker.modules.adult.AdultFilterSettings
import com.ecosentinel.appblocker.modules.adult.SupportedBrowsers
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureSettings
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

data class RemoteFeatureControl(
    val featureId: String,
    val enabled: Boolean,
    val metadataJson: String? = null,
    val source: String? = null,
    val updatedAt: String? = null,
    val appliedAt: String? = null
)

class RemoteFeatureControlBridge(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val focusManager = FocusManager(appContext)
    private val focusConfigStore = FocusConfigStore(appContext)

    fun applyControls(controls: List<RemoteFeatureControl>) {
        controls.forEach { control ->
            when (control.featureId) {
                FEATURE_ADULT_CONTENT -> AdultFilterSettings.setEnabled(appContext, control.enabled)
                FEATURE_YOUTUBE_SHORTS -> InAppFeatureSettings.setYoutubeShortsBlocked(appContext, control.enabled)
                FEATURE_INSTAGRAM_REELS -> InAppFeatureSettings.setInstagramReelsBlocked(appContext, control.enabled)
                FEATURE_BROWSER_INCOGNITO -> applyIncognitoControl(control)
                FEATURE_FOCUS_SESSION -> runBlocking { applyFocusControl(control) }
            }
        }
    }

    fun currentControls(): List<RemoteFeatureControl> {
        val focusSession = runBlocking { focusManager.getActiveSession() }
        return listOf(
            RemoteFeatureControl(
                featureId = FEATURE_ADULT_CONTENT,
                enabled = AdultFilterSettings.isEnabled(appContext),
                source = SOURCE_PHONE
            ),
            RemoteFeatureControl(
                featureId = FEATURE_YOUTUBE_SHORTS,
                enabled = InAppFeatureSettings.isYoutubeShortsBlocked(appContext),
                source = SOURCE_PHONE
            ),
            RemoteFeatureControl(
                featureId = FEATURE_INSTAGRAM_REELS,
                enabled = InAppFeatureSettings.isInstagramReelsBlocked(appContext),
                source = SOURCE_PHONE
            ),
            RemoteFeatureControl(
                featureId = FEATURE_BROWSER_INCOGNITO,
                enabled = InAppFeatureSettings.isBrowserIncognitoBlocked(appContext),
                metadataJson = browserIncognitoMetadata(),
                source = SOURCE_PHONE
            ),
            RemoteFeatureControl(
                featureId = FEATURE_FOCUS_SESSION,
                enabled = focusSession != null,
                metadataJson = if (focusSession != null) {
                    JSONObject()
                        .put("mode", focusSession.mode.name)
                        .put("startedAtMillis", focusSession.startedAtMillis)
                        .put("expiresAtMillis", focusSession.expiresAtMillis)
                        .put("blockedCategoryIds", JSONArray(focusManager.parseStringList(focusSession.blockedCategoryIdsJson)))
                        .put("blockedPackages", JSONArray(focusManager.parseStringList(focusSession.blockedPackagesJson)))
                        .toString()
                } else {
                    focusDraftMetadata()
                },
                source = SOURCE_PHONE
            )
        )
    }

    private fun applyIncognitoControl(control: RemoteFeatureControl) {
        val packages = metadataStringSet(control.metadataJson, "packages")
        if (packages != null) {
            InAppFeatureSettings.setBrowserIncognitoBlockedPackages(appContext, packages)
        } else if (
            control.enabled &&
            InAppFeatureSettings.getBrowserIncognitoBlockedPackages(appContext).isEmpty()
        ) {
            InAppFeatureSettings.setBrowserIncognitoBlockedPackages(appContext, defaultIncognitoPackages())
        }
        InAppFeatureSettings.setBrowserIncognitoBlocked(appContext, control.enabled)
    }

    private suspend fun applyFocusControl(control: RemoteFeatureControl) {
        val metadata = runCatching {
            JSONObject(control.metadataJson.orEmpty().ifBlank { "{}" })
        }.getOrDefault(JSONObject())
        val version = metadata.optString("commandId").ifBlank {
            control.updatedAt ?: control.metadataJson ?: control.enabled.toString()
        }
        if (!control.enabled) {
            focusManager.stopFocusFromRemote()
            saveLastFocusVersion(version)
            return
        }

        val blockedPackages = metadataStringSet(metadata, "blockedPackages")
        val blockedCategoryIds = metadataStringSet(metadata, "blockedCategoryIds")
        if (blockedPackages.isEmpty() && blockedCategoryIds.isEmpty()) {
            return
        }

        val activeSession = focusManager.getActiveSession()
        if (activeSession != null && lastFocusVersion() == version) {
            return
        }

        val durationMinutes = metadata.optInt("durationMinutes", FocusConfigStore.DEFAULT_DURATION_MINUTES)
            .coerceAtLeast(1)
        val mode = runCatching {
            FocusMode.valueOf(metadata.optString("mode", FocusMode.STRICT.name))
        }.getOrDefault(FocusMode.STRICT)

        focusManager.startFocus(
            blockedCategoryIds = blockedCategoryIds,
            blockedPackages = blockedPackages,
            durationMinutes = durationMinutes,
            mode = mode
        )
        saveLastFocusVersion(version)
    }

    private fun browserIncognitoMetadata(): String {
        return JSONObject()
            .put("packages", JSONArray(InAppFeatureSettings.getBrowserIncognitoBlockedPackages(appContext).toList()))
            .toString()
    }

    private fun focusDraftMetadata(): String {
        return JSONObject()
            .put("durationMinutes", focusConfigStore.getDurationMinutes())
            .put("mode", focusConfigStore.getMode().name)
            .put("blockedCategoryIds", JSONArray(focusConfigStore.getSelectedCategoryIds().toList()))
            .put("blockedPackages", JSONArray(focusConfigStore.getSelectedPackages().toList()))
            .toString()
    }

    private fun defaultIncognitoPackages(): Set<String> {
        val installed = InstalledAppsHelper.getAllInstalledApps(appContext).map { it.packageName }.toSet()
        return SupportedBrowsers.incognitoCapableBrowsers()
            .map { it.packageName }
            .filter { it in installed }
            .toSet()
            .ifEmpty { SupportedBrowsers.incognitoCapableBrowsers().map { it.packageName }.toSet() }
    }

    private fun metadataStringSet(json: String?, key: String): Set<String>? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching { metadataStringSet(JSONObject(json), key) }.getOrNull()
    }

    private fun metadataStringSet(json: JSONObject, key: String): Set<String> {
        val array = json.optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun lastFocusVersion(): String? = prefs.getString(KEY_LAST_FOCUS_VERSION, null)

    private fun saveLastFocusVersion(version: String) {
        prefs.edit().putString(KEY_LAST_FOCUS_VERSION, version).apply()
    }

    companion object {
        const val FEATURE_ADULT_CONTENT = "ADULT_CONTENT"
        const val FEATURE_YOUTUBE_SHORTS = "YOUTUBE_SHORTS"
        const val FEATURE_INSTAGRAM_REELS = "INSTAGRAM_REELS"
        const val FEATURE_BROWSER_INCOGNITO = "BROWSER_INCOGNITO"
        const val FEATURE_FOCUS_SESSION = "FOCUS_SESSION"

        private const val PREFS_NAME = "remote_feature_control_bridge"
        private const val KEY_LAST_FOCUS_VERSION = "last_focus_version"
        private const val SOURCE_PHONE = "PHONE"
    }
}
