package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

object InAppFeatureDetector {

    fun detect(
        packageName: String,
        root: AccessibilityNodeInfo,
        windowClassName: String? = null
    ): InAppFeature? {
        return when (packageName) {
            SupportedInAppApps.YOUTUBE -> {
                if (YouTubeShortsDetector.detect(root, windowClassName)) {
                    InAppFeature.YOUTUBE_SHORTS
                } else {
                    null
                }
            }
            SupportedInAppApps.INSTAGRAM -> {
                if (InstagramReelsDetector.detect(root, windowClassName)) {
                    InAppFeature.INSTAGRAM_REELS
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
