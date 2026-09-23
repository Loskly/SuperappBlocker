package com.ecosentinel.appblocker.modules.inapp

import android.view.accessibility.AccessibilityNodeInfo
import com.ecosentinel.appblocker.modules.adult.BrowserFamily
import com.ecosentinel.appblocker.modules.adult.SupportedBrowsers

object BrowserIncognitoDetector {

    private const val MAX_SCAN_DEPTH = 8
    private const val MAX_SCAN_NODES = 180
    private const val MATCH_THRESHOLD = 4

    private val privateViewIdFragments = setOf(
        "incognito",
        "private_browsing",
        "privatebrowser",
        "private_tab",
        "private_mode",
        "private_tabs",
        "privatetabs",
        "private_session"
    )

    private val browserUiViewIdFragments = setOf(
        "toolbar",
        "url_bar",
        "location_bar",
        "tab",
        "tabs",
        "browser_toolbar",
        "mozac_browser_toolbar",
        "awesomebar",
        "menu",
        "title",
        "privatebrowsingbutton",
        "private_browsing_button",
        "tab_counter",
        "tabs_tray",
        "tabstray"
    )

    private val firefoxPrivateToggleViewIdFragments = setOf(
        "privatebrowsingbutton",
        "private_browsing_button"
    )

    private val chromeStrongPhrases = setOf(
        "you've gone incognito",
        "you’ve gone incognito",
        "you are browsing privately",
        "you're browsing privately",
        "you’re browsing privately",
        "вы перешли в режим инкогнито"
    )

    private val chromeUiPhrases = setOf(
        "incognito tab",
        "new incognito tab",
        "close incognito tabs",
        "switch to incognito tabs",
        "incognito",
        "инкогнито",
        "новая вкладка инкогнито",
        "закрыть вкладки инкогнито"
    )

    private val firefoxStrongPhrases = setOf(
        "private browsing session",
        "private tabs open",
        "disable private browsing",
        "private browsing",
        "открытых приватных вкладок",
        "отключить приватный просмотр",
        "выключить приватный просмотр",
        "приватный просмотр"
    )

    private val firefoxUiPhrases = setOf(
        "private tab",
        "private tabs",
        "new private tab",
        "add private tab",
        "close private tabs",
        "unlock private tabs",
        "leave private tabs",
        "private browsing",
        "private",
        "приватная вкладка",
        "приватные вкладки",
        "приватных вкладок",
        "новая приватная вкладка",
        "добавить приватную вкладку",
        "закрыть приватные вкладки",
        "разблокировать приватные вкладки",
        "приватный просмотр"
    )

    private val firefoxActivePhrases = setOf(
        "disable private browsing",
        "add private tab",
        "new private tab",
        "close private tabs",
        "unlock private tabs",
        "leave private tabs",
        "отключить приватный просмотр",
        "выключить приватный просмотр",
        "добавить приватную вкладку",
        "новая приватная вкладка",
        "закрыть приватные вкладки",
        "разблокировать приватные вкладки"
    )

    private val firefoxSessionPhrases = setOf(
        "private browsing session",
        "private tabs open",
        "открытых приватных вкладок"
    )

    fun detect(packageName: String, root: AccessibilityNodeInfo): Boolean {
        if (!SupportedBrowsers.isIncognitoCapable(packageName)) {
            return false
        }

        val packagePrefix = "${packageName.lowercase()}:"
        val family = SupportedBrowsers.browserFamily(packageName)
        var score = 0

        return AccessibilityTreeScanner.anyNodeMatches(
            root = root,
            maxDepth = MAX_SCAN_DEPTH,
            maxNodes = MAX_SCAN_NODES
        ) { node ->
            val viewId = AccessibilityTreeScanner.viewId(node)
            val nodeScore = scoreNode(
                family = family,
                viewId = viewId,
                packagePrefix = packagePrefix,
                node = node
            )
            if (nodeScore > 0) {
                score = (score + nodeScore).coerceAtMost(MATCH_THRESHOLD)
            }
            score >= MATCH_THRESHOLD
        }
    }

    private fun scoreNode(
        family: BrowserFamily,
        viewId: String,
        packagePrefix: String,
        node: AccessibilityNodeInfo
    ): Int {
        if (family == BrowserFamily.FIREFOX) {
            return scoreFirefoxNode(viewId, packagePrefix, node)
        }

        if (isPrivateModeViewId(viewId, packagePrefix)) {
            return MATCH_THRESHOLD
        }

        val text = AccessibilityTreeScanner.nodeText(node)
        if (text.isEmpty()) {
            return 0
        }

        val strongPhrases = when (family) {
            BrowserFamily.CHROME -> chromeStrongPhrases
            BrowserFamily.OTHER -> emptySet()
            BrowserFamily.FIREFOX -> emptySet()
        }
        val uiPhrases = when (family) {
            BrowserFamily.CHROME -> chromeUiPhrases
            BrowserFamily.OTHER -> emptySet()
            BrowserFamily.FIREFOX -> emptySet()
        }

        if (containsAny(text, strongPhrases) && isShortUiLabel(text)) {
            return MATCH_THRESHOLD
        }

        if (!containsAny(text, uiPhrases)) {
            return 0
        }

        if (isBrowserUiViewId(viewId, packagePrefix)) {
            return MATCH_THRESHOLD
        }

        if (hasBrowserOwnedInteractiveSignal(node, packagePrefix) && isShortUiLabel(text)) {
            return MATCH_THRESHOLD
        }

        return 0
    }

    private fun scoreFirefoxNode(
        viewId: String,
        packagePrefix: String,
        node: AccessibilityNodeInfo
    ): Int {
        val text = AccessibilityTreeScanner.nodeText(node)
        if (text.isEmpty()) {
            return 0
        }

        if (containsAny(text, firefoxSessionPhrases) && isShortUiLabel(text)) {
            return MATCH_THRESHOLD
        }

        if (containsAny(text, firefoxActivePhrases) &&
            isShortUiLabel(text) &&
            (isBrowserUiViewId(viewId, packagePrefix) || hasBrowserOwnedInteractiveSignal(node, packagePrefix))
        ) {
            return MATCH_THRESHOLD
        }

        if (!containsAny(text, firefoxUiPhrases)) {
            return 0
        }

        val isPrivateToggle = isFirefoxPrivateToggleViewId(viewId, packagePrefix)
        if (isPrivateToggle && isActiveUiState(node)) {
            return MATCH_THRESHOLD
        }

        if (isBrowserUiViewId(viewId, packagePrefix) &&
            (isActiveUiState(node) || containsAny(text, firefoxActivePhrases))
        ) {
            return MATCH_THRESHOLD
        }

        if (hasBrowserOwnedInteractiveSignal(node, packagePrefix) &&
            isActiveUiState(node) &&
            isShortUiLabel(text)
        ) {
            return MATCH_THRESHOLD
        }

        if (containsAny(text, firefoxStrongPhrases) &&
            isShortUiLabel(text) &&
            hasBrowserOwnedInteractiveSignal(node, packagePrefix)
        ) {
            return MATCH_THRESHOLD
        }

        return 0
    }

    private fun isPrivateModeViewId(viewId: String, packagePrefix: String): Boolean {
        return viewId.startsWith(packagePrefix) &&
            privateViewIdFragments.any { fragment -> viewId.contains(fragment) }
    }

    private fun isBrowserUiViewId(viewId: String, packagePrefix: String): Boolean {
        return viewId.startsWith(packagePrefix) &&
            browserUiViewIdFragments.any { fragment -> viewId.contains(fragment) }
    }

    private fun isFirefoxPrivateToggleViewId(viewId: String, packagePrefix: String): Boolean {
        return viewId.startsWith(packagePrefix) &&
            firefoxPrivateToggleViewIdFragments.any { fragment -> viewId.contains(fragment) }
    }

    private fun hasInteractiveUiState(node: AccessibilityNodeInfo): Boolean {
        return node.isSelected ||
            node.isChecked ||
            node.isCheckable ||
            node.isClickable ||
            node.isFocusable
    }

    private fun isActiveUiState(node: AccessibilityNodeInfo): Boolean {
        return node.isSelected || node.isChecked
    }

    private fun hasBrowserOwnedInteractiveSignal(
        node: AccessibilityNodeInfo,
        packagePrefix: String
    ): Boolean {
        val viewId = AccessibilityTreeScanner.viewId(node)
        if (!viewId.startsWith(packagePrefix)) {
            return false
        }
        return hasInteractiveUiState(node)
    }

    private fun isShortUiLabel(text: String): Boolean {
        if (text.length > 90) {
            return false
        }
        return text.count { it.isWhitespace() } <= 10
    }

    private fun containsAny(text: String, phrases: Set<String>): Boolean {
        return phrases.any { phrase -> text.contains(phrase) }
    }
}
