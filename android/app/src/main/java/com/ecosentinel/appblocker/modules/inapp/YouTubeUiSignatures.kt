package com.ecosentinel.appblocker.modules.inapp

/**
 * Accessibility fingerprints of the YouTube Shorts *player* (tab, feed, search, notifications).
 * View IDs change between YouTube versions — keep several partial matches.
 */
object YouTubeUiSignatures {

    val PLAYER_VIEW_ID_FRAGMENTS = setOf(
        "reel_recycler",
        "reel_player",
        "reel_watch",
        "shorts_player",
        "shorts_video",
        "reel_player_page",
        "reels_watch",
        "vertical_shorts",
        "shorts_fragment",
        "shorts_container"
    )

    /** Home-feed Shorts shelf/carousel — not an active player. */
    val SHELF_VIEW_ID_FRAGMENTS = setOf(
        "shorts_shelf",
        "shelf_element",
        "chip_cloud"
    )

    val PLAYER_CONTENT_PHRASES = setOf(
        "swipe up for next short",
        "swipe up for the next short",
        "like this short",
        "dislike this short",
        "next short",
        "previous short",
        "shorts player",
        "пролистните вверх",
        "следующий short",
        "понравился short",
        "не понравился short"
    )

    val WINDOW_CLASS_FRAGMENTS = setOf(
        "shorts",
        "reelwatch",
        "reel_watch",
        "reelswatch"
    )

    val WINDOW_CLASS_EXCLUDE_FRAGMENTS = setOf(
        "shelf",
        "search"
    )
}
