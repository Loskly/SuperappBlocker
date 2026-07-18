package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

object YouTubeShortsDetector {

    private val tabKeywords = setOf("shorts", "шортс")

    fun detect(root: AccessibilityNodeInfo, windowClassName: String? = null): Boolean {
        return AccessibilityTreeScanner.anyNodeMatches(root, ::isSelectedShortsTab) ||
            isShortsPlayerVisible(root) ||
            InAppPlayerDetector.matchesWindowClass(
                windowClassName = windowClassName,
                includeFragments = YouTubeUiSignatures.WINDOW_CLASS_FRAGMENTS,
                excludeFragments = YouTubeUiSignatures.WINDOW_CLASS_EXCLUDE_FRAGMENTS
            )
    }

    private fun isSelectedShortsTab(node: AccessibilityNodeInfo): Boolean {
        if (!node.isSelected) {
            return false
        }
        return AccessibilityTreeScanner.containsAnyKeyword(
            AccessibilityTreeScanner.nodeText(node),
            tabKeywords
        )
    }

    private fun isShortsPlayerVisible(root: AccessibilityNodeInfo): Boolean {
        if (AccessibilityTreeScanner.anyViewIdContains(
                root,
                YouTubeUiSignatures.PLAYER_VIEW_ID_FRAGMENTS
            )
        ) {
            return true
        }
        if (AccessibilityTreeScanner.anyViewIdContains(root, YouTubeUiSignatures.SHELF_VIEW_ID_FRAGMENTS)) {
            return false
        }
        return InAppPlayerDetector.hasPlayerContentPhrase(
            root,
            YouTubeUiSignatures.PLAYER_CONTENT_PHRASES
        )
    }
}
