package com.ecosentinel.appblocker.modules.inapp

import android.content.Context

object InAppFeatureSettings {

    private const val PREFS_NAME = "in_app_feature_prefs"
    private const val KEY_YOUTUBE_SHORTS = "block_youtube_shorts"
    private const val KEY_INSTAGRAM_REELS = "block_instagram_reels"

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

    fun isAnyBlocked(context: Context): Boolean {
        return isYoutubeShortsBlocked(context) || isInstagramReelsBlocked(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
