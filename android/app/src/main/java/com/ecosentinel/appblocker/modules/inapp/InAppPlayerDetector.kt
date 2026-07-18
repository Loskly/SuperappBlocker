package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

internal object InAppPlayerDetector {

    fun hasPlayerContentPhrase(
        root: AccessibilityNodeInfo,
        phrases: Set<String>
    ): Boolean {
        return AccessibilityTreeScanner.anyNodeMatches(root) { node ->
            if (node.isSelected) {
                return@anyNodeMatches false
            }
            val text = AccessibilityTreeScanner.nodeText(node)
            phrases.any { phrase -> text.contains(phrase) }
        }
    }

    fun matchesWindowClass(
        windowClassName: String?,
        includeFragments: Set<String>,
        excludeFragments: Set<String>
    ): Boolean {
        val className = windowClassName?.lowercase().orEmpty()
        if (className.isEmpty()) {
            return false
        }
        if (excludeFragments.any { className.contains(it) }) {
            return false
        }
        return includeFragments.any { className.contains(it) }
    }
}
