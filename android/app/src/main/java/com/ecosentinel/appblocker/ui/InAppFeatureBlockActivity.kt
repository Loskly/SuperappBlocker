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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInAppFeatureBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGrantAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
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
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
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
}
