package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

object YouTubeShortsDetector {

    private val tabKeywords = setOf("shorts", "шортс")

    fun detect(root: AccessibilityNodeInfo): Boolean {
        return AccessibilityTreeScanner.anyNodeMatches(root, ::isSelectedShortsTab)
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
}
