package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivitySurvivalModeBinding
import com.ecosentinel.appblocker.survival.SurvivalAccessibilityRequirementMode
import com.ecosentinel.appblocker.survival.SurvivalManager
import com.ecosentinel.appblocker.survival.SurvivalSettings
import com.ecosentinel.appblocker.util.PermissionHelper

class SurvivalModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySurvivalModeBinding
    private var updatingUi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurvivalModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGrantAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }
        binding.btnRunSurvivalCheck.setOnClickListener {
            SurvivalManager.runCheck(this, SurvivalManager.REASON_MANUAL) {
                runOnUiThread { refreshUi() }
            }
        }
        binding.requireAccessibilitySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingUi) {
                return@setOnCheckedChangeListener
            }
            SurvivalSettings.setAccessibilityRequirementEnabled(this, isChecked)
            applySettingsChange()
        }
        binding.accessibilityPromptModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (updatingUi) {
                return@setOnCheckedChangeListener
            }
            val mode = when (checkedId) {
                R.id.overlayModeRadio -> SurvivalAccessibilityRequirementMode.OVERLAY
                else -> SurvivalAccessibilityRequirementMode.NOTIFICATION
            }
            SurvivalSettings.setAccessibilityRequirementMode(this, mode)
            applySettingsChange()
        }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun applySettingsChange() {
        refreshUi()
        SurvivalManager.runCheck(this, SurvivalManager.REASON_MANUAL) {
            runOnUiThread { refreshUi() }
        }
    }

    private fun refreshUi() {
        updatingUi = true

        val requireAccessibility = SurvivalSettings.isAccessibilityRequirementEnabled(this)
        val mode = SurvivalSettings.getAccessibilityRequirementMode(this)
        binding.requireAccessibilitySwitch.isChecked = requireAccessibility
        binding.accessibilityPromptModeGroup.check(
            when (mode) {
                SurvivalAccessibilityRequirementMode.NOTIFICATION -> R.id.notificationModeRadio
                SurvivalAccessibilityRequirementMode.OVERLAY -> R.id.overlayModeRadio
            }
        )
        binding.modeContainer.visibility = if (requireAccessibility) View.VISIBLE else View.GONE

        updatingUi = false
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
