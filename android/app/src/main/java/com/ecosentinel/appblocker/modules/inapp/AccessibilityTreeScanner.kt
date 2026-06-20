package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

internal object AccessibilityTreeScanner {

    fun anyNodeMatches(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        if (predicate(root)) {
            return true
        }
        for (index in 0 until root.childCount) {
            val child = root.getChild(index) ?: continue
            try {
                if (anyNodeMatches(child, predicate)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }

    fun nodeText(node: AccessibilityNodeInfo): String {
        return buildString {
            node.text?.toString()?.trim()?.let { append(it).append(' ') }
            node.contentDescription?.toString()?.trim()?.let { append(it) }
        }.trim().lowercase()
    }

    fun viewId(node: AccessibilityNodeInfo): String {
        return node.viewIdResourceName?.lowercase().orEmpty()
    }

    fun containsAnyKeyword(text: String, keywords: Set<String>): Boolean {
        if (text.isEmpty()) {
            return false
        }
        return keywords.any { keyword -> text.contains(keyword) }
    }
}
