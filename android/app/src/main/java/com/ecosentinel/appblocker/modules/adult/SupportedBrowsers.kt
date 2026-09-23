package com.ecosentinel.appblocker.modules.adult

enum class BrowserFamily {
    CHROME,
    FIREFOX,
    OTHER
}

data class SupportedBrowser(
    val packageName: String,
    val fallbackLabel: String,
    val family: BrowserFamily,
    val supportsIncognitoBlock: Boolean
)

object SupportedBrowsers {

    private val browsers = listOf(
        SupportedBrowser("com.android.chrome", "Chrome", BrowserFamily.CHROME, true),
        SupportedBrowser("com.chrome.beta", "Chrome Beta", BrowserFamily.CHROME, true),
        SupportedBrowser("com.chrome.dev", "Chrome Dev", BrowserFamily.CHROME, true),
        SupportedBrowser("com.chrome.canary", "Chrome Canary", BrowserFamily.CHROME, true),
        SupportedBrowser("org.mozilla.firefox", "Firefox", BrowserFamily.FIREFOX, true),
        SupportedBrowser("org.mozilla.firefox_beta", "Firefox Beta", BrowserFamily.FIREFOX, true),
        SupportedBrowser("org.mozilla.fenix", "Firefox", BrowserFamily.FIREFOX, true),
        SupportedBrowser("com.microsoft.emmx", "Microsoft Edge", BrowserFamily.OTHER, false),
        SupportedBrowser("com.sec.android.app.sbrowser", "Samsung Internet", BrowserFamily.OTHER, false),
        SupportedBrowser("com.opera.browser", "Opera", BrowserFamily.OTHER, false),
        SupportedBrowser("com.opera.mini.native", "Opera Mini", BrowserFamily.OTHER, false),
        SupportedBrowser("com.brave.browser", "Brave", BrowserFamily.OTHER, false),
        SupportedBrowser("com.yandex.browser", "Yandex Browser", BrowserFamily.OTHER, false),
        SupportedBrowser("com.vivaldi.browser", "Vivaldi", BrowserFamily.OTHER, false),
        SupportedBrowser("com.duckduckgo.mobile.android", "DuckDuckGo", BrowserFamily.OTHER, false),
        SupportedBrowser("com.kiwibrowser.browser", "Kiwi Browser", BrowserFamily.OTHER, false)
    )

    private val browserPackages = browsers.map { it.packageName }.toSet()

    private val urlBarViewIds = setOf(
        "com.android.chrome:id/url_bar",
        "com.chrome.beta:id/url_bar",
        "com.chrome.dev:id/url_bar",
        "com.chrome.canary:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.firefox:id/url_bar_title",
        "org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view",
        "org.mozilla.firefox_beta:id/url_bar_title",
        "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        "org.mozilla.fenix:id/url_bar_title",
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

    fun incognitoCapableBrowsers(): List<SupportedBrowser> {
        return browsers.filter { it.supportsIncognitoBlock }
    }

    fun isIncognitoCapable(packageName: String): Boolean {
        return browserForPackage(packageName)?.supportsIncognitoBlock == true
    }

    fun browserFamily(packageName: String): BrowserFamily {
        return browserForPackage(packageName)?.family ?: BrowserFamily.OTHER
    }

    fun browserForPackage(packageName: String): SupportedBrowser? {
        return browsers.firstOrNull { it.packageName == packageName }
    }

    fun urlBarViewIdsFor(packageName: String): Set<String> {
        val ids = urlBarViewIds.filter { it.startsWith("$packageName:") }.toSet()
        if (ids.isNotEmpty()) {
            return ids
        }
        return urlBarViewIds
    }
}
