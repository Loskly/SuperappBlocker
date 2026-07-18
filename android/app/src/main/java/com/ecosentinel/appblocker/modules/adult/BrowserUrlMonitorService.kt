package com.ecosentinel.appblocker.modules.adult



import android.accessibilityservice.AccessibilityService

import android.view.accessibility.AccessibilityEvent

import android.view.accessibility.AccessibilityNodeInfo

import com.ecosentinel.appblocker.modules.inapp.InAppFeatureDetector

import com.ecosentinel.appblocker.modules.inapp.InAppFeatureSettings

import com.ecosentinel.appblocker.modules.inapp.InAppFeatureState

import com.ecosentinel.appblocker.modules.inapp.SupportedInAppApps



class BrowserUrlMonitorService : AccessibilityService() {



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



    private fun handleBrowser(root: AccessibilityNodeInfo, packageName: String) {

        val url = extractUrl(root, packageName) ?: return

        BrowserUrlState.update(packageName, url)

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

}


