package com.ecosentinel.appblocker.modules.adult

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ecosentinel.appblocker.modules.inapp.BrowserIncognitoDetector
import com.ecosentinel.appblocker.modules.inapp.InAppFeature
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureDetector
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureSettings
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureState
import com.ecosentinel.appblocker.modules.inapp.SupportedInAppApps
import com.ecosentinel.appblocker.survival.SurvivalManager

class BrowserUrlMonitorService : AccessibilityService() {

    private data class DebouncedFeatureState(
        val feature: InAppFeature?,
        val updatedAtMillis: Long
    )

    private val incognitoFeatureUpdates = mutableMapOf<String, DebouncedFeatureState>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected — triggering survival check")
        SurvivalManager.runCheck(this, SurvivalManager.REASON_ACCESSIBILITY_RECONNECTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> Unit
            else -> return
        }

        val packageName = event.packageName?.toString() ?: return
        val windowClassName = event.className?.toString()
        val root = rootInActiveWindow ?: return
        try {
            when {
                SupportedBrowsers.isBrowser(packageName) -> handleBrowser(root, packageName)
                SupportedInAppApps.isSupported(packageName) -> handleInApp(root, packageName, windowClassName)
            }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }


    private fun handleBrowser(root: AccessibilityNodeInfo, packageName: String) {
        handleBrowserIncognito(root, packageName)

        val url = extractUrl(root, packageName) ?: return
        BrowserUrlState.update(packageName, url)
    }

    private fun handleBrowserIncognito(root: AccessibilityNodeInfo, packageName: String) {
        if (!SupportedBrowsers.isIncognitoCapable(packageName) ||
            !InAppFeatureSettings.isBrowserIncognitoBlockedForPackage(this, packageName)
        ) {
            updateIncognitoFeature(packageName, null)
            return
        }

        val feature = if (BrowserIncognitoDetector.detect(packageName, root)) {
            InAppFeature.BROWSER_INCOGNITO
        } else {
            null
        }
        updateIncognitoFeature(packageName, feature)
    }

    private fun updateIncognitoFeature(packageName: String, feature: InAppFeature?) {
        val nowMillis = System.currentTimeMillis()
        val previous = incognitoFeatureUpdates[packageName]
        if (previous != null &&
            previous.feature == feature &&
            nowMillis - previous.updatedAtMillis < INCOGNITO_STATE_UPDATE_COOLDOWN_MS
        ) {
            return
        }

        incognitoFeatureUpdates[packageName] = DebouncedFeatureState(feature, nowMillis)
        if (feature == null) {
            InAppFeatureState.clearForPackage(packageName)
        } else {
            InAppFeatureState.update(packageName, feature)
        }
    }

    private fun handleInApp(
        root: AccessibilityNodeInfo,
        packageName: String,
        windowClassName: String?
    ) {
        val shouldDetect = when (packageName) {
            SupportedInAppApps.YOUTUBE -> InAppFeatureSettings.isYoutubeShortsBlocked(this)
            SupportedInAppApps.INSTAGRAM -> InAppFeatureSettings.isInstagramReelsBlocked(this)
            else -> false
        }
        if (!shouldDetect) {
            InAppFeatureState.clearForPackage(packageName)
            return
        }

        val feature = InAppFeatureDetector.detect(packageName, root, windowClassName)
        if (feature == null) {
            InAppFeatureState.clearForPackage(packageName)
        } else {
            InAppFeatureState.update(packageName, feature)
        }
    }

    private fun extractUrl(root: AccessibilityNodeInfo, packageName: String): String? {
        for (viewId in SupportedBrowsers.urlBarViewIdsFor(packageName)) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            try {
                for (node in nodes) {
                    val text = node.text?.toString()?.trim().orEmpty()
                    if (looksLikeUrl(text)) {
                        return text
                    }
                }
            } finally {
                nodes.forEach { it.recycle() }
            }
        }

        return findUrlInTree(root)
    }

    private fun findUrlInTree(node: AccessibilityNodeInfo): String? {
        val text = node.text?.toString()?.trim().orEmpty()
        if (looksLikeUrl(text)) {
            return text
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                findUrlInTree(child)?.let { return it }
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun looksLikeUrl(text: String): Boolean {
        if (text.length < 4) {
            return false
        }
        if (text.contains(' ')) {
            return false
        }
        return text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true) ||
            (text.contains('.') && !text.startsWith("."))
    }

    companion object {
        private const val TAG = "BrowserUrlMonitor"
        private const val INCOGNITO_STATE_UPDATE_COOLDOWN_MS = 750L
    }
}

