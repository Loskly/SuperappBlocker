package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ecosentinel.appblocker.databinding.ActivitySurvivalAccessibilityOverlayBinding
import com.ecosentinel.appblocker.survival.AppHealthChecker
import com.ecosentinel.appblocker.survival.SurvivalAccessibilityOverlayManager
import com.ecosentinel.appblocker.survival.SurvivalSettings
import com.ecosentinel.appblocker.util.PermissionHelper

class SurvivalAccessibilityOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurvivalAccessibilityOverlayBinding
    private var openSettingsOnResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurvivalAccessibilityOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openSettingsOnResume = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        binding.btnOpenAccessibility.setOnClickListener {
            SurvivalAccessibilityOverlayManager.scheduleRechecks(this)
            PermissionHelper.openAccessibilitySettings(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openSettingsOnResume = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
    }

    override fun onResume() {
        super.onResume()
        val snapshot = AppHealthChecker.check(this)
        if (!SurvivalSettings.shouldShowAccessibilityOverlay(this, snapshot)) {
            finish()
            return
        }
        if (openSettingsOnResume) {
            openSettingsOnResume = false
            binding.root.post {
                SurvivalAccessibilityOverlayManager.scheduleRechecks(this)
                PermissionHelper.openAccessibilitySettings(this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        SurvivalAccessibilityOverlayManager.scheduleRechecks(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    companion object {
        private const val EXTRA_OPEN_SETTINGS = "extra_open_settings"

        fun launch(context: Context, openSettings: Boolean = false) {
            val intent = Intent(context, SurvivalAccessibilityOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(EXTRA_OPEN_SETTINGS, openSettings)
            }
            context.startActivity(intent)
        }
    }
}
