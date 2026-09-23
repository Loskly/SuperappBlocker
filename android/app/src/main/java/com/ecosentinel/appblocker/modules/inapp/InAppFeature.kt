package com.ecosentinel.appblocker.modules.inapp

enum class InAppFeature {
    YOUTUBE_SHORTS,
    INSTAGRAM_REELS,
    BROWSER_INCOGNITO;

    val blockReason: String
        get() = when (this) {
            YOUTUBE_SHORTS -> REASON_YOUTUBE_SHORTS
            INSTAGRAM_REELS -> REASON_INSTAGRAM_REELS
            BROWSER_INCOGNITO -> REASON_BROWSER_INCOGNITO
        }

    companion object {
        const val REASON_YOUTUBE_SHORTS = "youtube_shorts"
        const val REASON_INSTAGRAM_REELS = "instagram_reels"
        const val REASON_BROWSER_INCOGNITO = "browser_incognito"
    }
}
