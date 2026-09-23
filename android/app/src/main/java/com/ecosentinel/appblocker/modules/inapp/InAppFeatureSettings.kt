package com.ecosentinel.appblocker.modules.inapp

import android.content.Context

object InAppFeatureSettings {

    private const val PREFS_NAME = "in_app_feature_prefs"
    private const val KEY_YOUTUBE_SHORTS = "block_youtube_shorts"
    private const val KEY_INSTAGRAM_REELS = "block_instagram_reels"
    private const val KEY_BROWSER_INCOGNITO = "block_browser_incognito"
    private const val KEY_BROWSER_INCOGNITO_PACKAGES = "block_browser_incognito_packages"

    fun isYoutubeShortsBlocked(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_YOUTUBE_SHORTS, false)
    }

    fun setYoutubeShortsBlocked(context: Context, blocked: Boolean) {
        prefs(context).edit().putBoolean(KEY_YOUTUBE_SHORTS, blocked).apply()
    }

    fun isInstagramReelsBlocked(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_INSTAGRAM_REELS, false)
    }

    fun setInstagramReelsBlocked(context: Context, blocked: Boolean) {
        prefs(context).edit().putBoolean(KEY_INSTAGRAM_REELS, blocked).apply()
    }

    fun isBrowserIncognitoBlocked(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_BROWSER_INCOGNITO, false)
    }

    fun setBrowserIncognitoBlocked(context: Context, blocked: Boolean) {
        prefs(context).edit().putBoolean(KEY_BROWSER_INCOGNITO, blocked).apply()
    }

    fun getBrowserIncognitoBlockedPackages(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_BROWSER_INCOGNITO_PACKAGES, emptySet()).orEmpty()
    }

    fun setBrowserIncognitoBlockedPackages(context: Context, packages: Set<String>) {
        prefs(context).edit()
            .putStringSet(KEY_BROWSER_INCOGNITO_PACKAGES, packages.toSet())
            .apply()
    }

    fun isBrowserIncognitoBlockedForPackage(context: Context, packageName: String): Boolean {
        return isBrowserIncognitoBlocked(context) &&
            packageName in getBrowserIncognitoBlockedPackages(context)
    }

    fun setBrowserIncognitoBlockedForPackage(
        context: Context,
        packageName: String,
        blocked: Boolean
    ) {
        val updated = getBrowserIncognitoBlockedPackages(context).toMutableSet()
        if (blocked) {
            updated += packageName
        } else {
            updated -= packageName
        }
        setBrowserIncognitoBlockedPackages(context, updated)
    }

    fun isAnyBlocked(context: Context): Boolean {
        return isYoutubeShortsBlocked(context) ||
            isInstagramReelsBlocked(context) ||
            (isBrowserIncognitoBlocked(context) && getBrowserIncognitoBlockedPackages(context).isNotEmpty())
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
