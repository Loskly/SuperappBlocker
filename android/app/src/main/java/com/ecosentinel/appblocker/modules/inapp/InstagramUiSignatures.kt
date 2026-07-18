package com.ecosentinel.appblocker.modules.inapp

/**
 * Accessibility fingerprints of the Instagram Reels *player* (tab, feed, explore, profile, DMs).
 */
object InstagramUiSignatures {

    val PLAYER_VIEW_ID_FRAGMENTS = setOf(
        "clips_viewer",
        "clips_video",
        "reel_viewer",
        "reels_viewer",
        "clips_component",
        "ig_reels",
        "reels_media",
        "clips_viewer_view_pager"
    )

    /** Grid/shelf previews - not fullscreen playback. */
    val SHELF_VIEW_ID_FRAGMENTS = setOf(
        "clips_grid",
        "reel_grid",
        "profile_clips"
    )

    val PLAYER_CONTENT_PHRASES = setOf(
        "reel by",
        "reels by",
        "clip by",
        "reels audio",
        "original audio",
        "открыть reels",
        "открыть reel"
    )

    val WINDOW_CLASS_FRAGMENTS = setOf(
        "clips",
        "reel"
    )

    val WINDOW_CLASS_EXCLUDE_FRAGMENTS = setOf(
        "grid",
        "search"
    )
}
