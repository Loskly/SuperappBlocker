package com.ecosentinel.appblocker.modules.adult

object SupportedBrowsers {

    private val browserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.yandex.browser",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser"
    )

    private val urlBarViewIds = setOf(
        "com.android.chrome:id/url_bar",
        "com.chrome.beta:id/url_bar",
        "com.chrome.dev:id/url_bar",
        "com.chrome.canary:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.firefox:id/url_bar_title",
        "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx:id/url_bar",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.opera.browser:id/url_field",
        "com.brave.browser:id/url_bar",
        "com.yandex.browser:id/bro_omnibox",
        "com.vivaldi.browser:id/url_bar",
        "com.duckduckgo.mobile.android:id/omnibarTextInput",
        "com.kiwibrowser.browser:id/url_bar"
    )

    fun isBrowser(packageName: String): Boolean {
        if (packageName in browserPackages) {
            return true
        }
        val lower = packageName.lowercase()
        return lower.contains("chrome") ||
            lower.contains("firefox") ||
            lower.contains("browser") ||
            lower.contains("opera")
    }

    fun urlBarViewIdsFor(packageName: String): Set<String> {
        val ids = urlBarViewIds.filter { it.startsWith("$packageName:") }.toSet()
        if (ids.isNotEmpty()) {
            return ids
        }
        return urlBarViewIds
    }
}
