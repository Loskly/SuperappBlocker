package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

object InstagramReelsDetector {

    private val tabKeywords = setOf("reels", "reel", "рилс", "рилсы")

    fun detect(root: AccessibilityNodeInfo, windowClassName: String? = null): Boolean {
        return AccessibilityTreeScanner.anyNodeMatches(root, ::isSelectedReelsTab) ||
            isReelsPlayerVisible(root) ||
            InAppPlayerDetector.matchesWindowClass(
                windowClassName = windowClassName,
                includeFragments = InstagramUiSignatures.WINDOW_CLASS_FRAGMENTS,
                excludeFragments = InstagramUiSignatures.WINDOW_CLASS_EXCLUDE_FRAGMENTS
            )
    }

    private fun isSelectedReelsTab(node: AccessibilityNodeInfo): Boolean {
        if (!node.isSelected) {
            return false
        }
        return AccessibilityTreeScanner.containsAnyKeyword(
            AccessibilityTreeScanner.nodeText(node),
            tabKeywords
        )
    }

    private fun isReelsPlayerVisible(root: AccessibilityNodeInfo): Boolean {
        if (AccessibilityTreeScanner.anyViewIdContains(
                root,
                InstagramUiSignatures.PLAYER_VIEW_ID_FRAGMENTS
            )
        ) {
            return true
        }
        if (AccessibilityTreeScanner.anyViewIdContains(root, InstagramUiSignatures.SHELF_VIEW_ID_FRAGMENTS)) {
            return false
        }
        return InAppPlayerDetector.hasPlayerContentPhrase(
            root,
            InstagramUiSignatures.PLAYER_CONTENT_PHRASES
        )
    }
}
