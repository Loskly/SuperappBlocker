package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo

internal object AccessibilityTreeScanner {

    fun anyNodeMatches(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        return anyNodeMatches(
            root = root,
            maxDepth = Int.MAX_VALUE,
            maxNodes = Int.MAX_VALUE,
            predicate = predicate
        )
    }

    fun anyNodeMatches(
        root: AccessibilityNodeInfo,
        maxDepth: Int,
        maxNodes: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): Boolean {
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int): Boolean {
            if (visited >= maxNodes) {
                return false
            }
            visited += 1
            if (predicate(node)) {
                return true
            }
            if (depth >= maxDepth) {
                return false
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    if (visit(child, depth + 1)) {
                        return true
                    }
                } finally {
                    child.recycle()
                }
            }
            return false
        }

        if (maxDepth < 0 || maxNodes <= 0) {
            return false
        }
        if (visit(root, depth = 0)) {
            return true
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

    fun forEachNode(root: AccessibilityNodeInfo, action: (AccessibilityNodeInfo) -> Unit) {
        forEachNode(
            root = root,
            maxDepth = Int.MAX_VALUE,
            maxNodes = Int.MAX_VALUE,
            action = action
        )
    }

    fun forEachNode(
        root: AccessibilityNodeInfo,
        maxDepth: Int,
        maxNodes: Int,
        action: (AccessibilityNodeInfo) -> Unit
    ) {
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= maxNodes) {
                return
            }
            visited += 1
            action(node)
            if (depth >= maxDepth) {
                return
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, depth + 1)
                } finally {
                    child.recycle()
                }
            }
        }

        if (maxDepth < 0 || maxNodes <= 0) {
            return
        }
        visit(root, depth = 0)
    }

    fun anyViewIdContains(root: AccessibilityNodeInfo, fragments: Set<String>): Boolean {
        return anyNodeMatches(root) { node ->
            val viewId = viewId(node)
            viewId.isNotEmpty() && fragments.any { viewId.contains(it) }
        }
    }
}
