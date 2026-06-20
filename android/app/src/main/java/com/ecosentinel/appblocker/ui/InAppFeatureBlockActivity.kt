package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityInAppFeatureBlockBinding
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureSettings
import com.ecosentinel.appblocker.modules.inapp.SupportedInAppApps
import com.ecosentinel.appblocker.util.PermissionHelper

class InAppFeatureBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInAppFeatureBlockBinding
    private var showingAllSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInAppFeatureBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showingAllSettings = savedInstanceState?.getBoolean(STATE_SHOW_ALL, false)
            ?: (intent.getStringExtra(EXTRA_FOCUS) == null)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGrantAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }
        binding.btnShowAllSettings.setOnClickListener {
            showingAllSettings = true
            applyFocusMode()
        }

        binding.youtubeShortsSwitch.setOnCheckedChangeListener(null)
        binding.instagramReelsSwitch.setOnCheckedChangeListener(null)
        binding.youtubeShortsSwitch.isChecked = InAppFeatureSettings.isYoutubeShortsBlocked(this)
        binding.instagramReelsSwitch.isChecked = InAppFeatureSettings.isInstagramReelsBlocked(this)
        binding.youtubeShortsSwitch.setOnCheckedChangeListener { _, isChecked ->
            InAppFeatureSettings.setYoutubeShortsBlocked(this, isChecked)
        }
        binding.instagramReelsSwitch.setOnCheckedChangeListener { _, isChecked ->
            InAppFeatureSettings.setInstagramReelsBlocked(this, isChecked)
        }
        binding.btnTryYoutubeShorts.setOnClickListener {
            BlockOverlayManager.show(
                this,
                SupportedInAppApps.YOUTUBE,
                BlockOverlayActivity.REASON_YOUTUBE_SHORTS
            )
        }
        binding.btnTryInstagramReels.setOnClickListener {
            BlockOverlayManager.show(
                this,
                SupportedInAppApps.INSTAGRAM,
                BlockOverlayActivity.REASON_INSTAGRAM_REELS
            )
        }

        applyFocusMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SHOW_ALL, showingAllSettings)
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
    }

    private fun applyFocusMode() {
        when (intent.getStringExtra(EXTRA_FOCUS)) {
            FOCUS_YOUTUBE_SHORTS -> {
                binding.screenTitle.text = getString(R.string.feature_youtube_shorts_title)
                binding.youtubeSection.visibility = View.VISIBLE
                binding.instagramSection.visibility =
                    if (showingAllSettings) View.VISIBLE else View.GONE
                binding.btnShowAllSettings.visibility =
                    if (showingAllSettings) View.GONE else View.VISIBLE
            }
            FOCUS_INSTAGRAM_REELS -> {
                binding.screenTitle.text = getString(R.string.feature_instagram_reels_title)
                binding.instagramSection.visibility = View.VISIBLE
                binding.youtubeSection.visibility =
                    if (showingAllSettings) View.VISIBLE else View.GONE
                binding.btnShowAllSettings.visibility =
                    if (showingAllSettings) View.GONE else View.VISIBLE
            }
            else -> {
                binding.screenTitle.text = getString(R.string.in_app_feature_screen_title)
                binding.youtubeSection.visibility = View.VISIBLE
                binding.instagramSection.visibility = View.VISIBLE
                binding.btnShowAllSettings.visibility = View.GONE
            }
        }
    }

    private fun refreshAccessibilityStatus() {
        val accessibilityGranted = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.accessibilityStatusText.text = getString(
            R.string.status_line,
            getString(R.string.stayfree_accessibility_title),
            if (accessibilityGranted) getString(R.string.ok_status) else getString(R.string.no_status)
        )
        binding.btnGrantAccessibility.visibility =
            if (accessibilityGranted) View.GONE else View.VISIBLE
    }

    companion object {
        private const val STATE_SHOW_ALL = "show_all_settings"
        private const val EXTRA_FOCUS = "focus"
        const val FOCUS_EXTRA = EXTRA_FOCUS
        const val FOCUS_YOUTUBE_SHORTS = "youtube_shorts"
        const val FOCUS_INSTAGRAM_REELS = "instagram_reels"
    }
}
