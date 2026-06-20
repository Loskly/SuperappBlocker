package com.ecosentinel.appblocker.modules.inapp

enum class InAppFeature {
    YOUTUBE_SHORTS,
    INSTAGRAM_REELS;

    val blockReason: String
        get() = when (this) {
            YOUTUBE_SHORTS -> REASON_YOUTUBE_SHORTS
            INSTAGRAM_REELS -> REASON_INSTAGRAM_REELS
        }

    companion object {
        const val REASON_YOUTUBE_SHORTS = "youtube_shorts"
        const val REASON_INSTAGRAM_REELS = "instagram_reels"
    }
}
