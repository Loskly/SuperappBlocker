package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

object InstagramReelsDetector {

    private val tabKeywords = setOf("reels", "reel", "рилс", "рилсы")

    fun detect(root: AccessibilityNodeInfo): Boolean {
        return AccessibilityTreeScanner.anyNodeMatches(root, ::isSelectedReelsTab)
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
}
