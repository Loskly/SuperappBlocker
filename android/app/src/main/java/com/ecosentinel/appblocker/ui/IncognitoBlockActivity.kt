package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityIncognitoBlockBinding
import com.ecosentinel.appblocker.databinding.ItemIncognitoBrowserBinding
import com.ecosentinel.appblocker.modules.adult.BrowserFamily
import com.ecosentinel.appblocker.modules.adult.SupportedBrowser
import com.ecosentinel.appblocker.modules.adult.SupportedBrowsers
import com.ecosentinel.appblocker.modules.inapp.InAppFeatureSettings
import com.ecosentinel.appblocker.util.PermissionHelper

class IncognitoBlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncognitoBlockBinding
    private lateinit var adapter: IncognitoBrowserAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncognitoBlockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = IncognitoBrowserAdapter { packageName, blocked ->
            InAppFeatureSettings.setBrowserIncognitoBlockedForPackage(this, packageName, blocked)
            refreshBrowsers()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGrantAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }
        binding.btnTryBlock.setOnClickListener {
            val packageName = adapter.currentList.firstOrNull { it.blocked }?.packageName
                ?: adapter.currentList.firstOrNull()?.packageName
                ?: SupportedBrowsers.incognitoCapableBrowsers().first().packageName
            BlockOverlayManager.show(this, packageName, BlockOverlayActivity.REASON_BROWSER_INCOGNITO)
        }
        binding.browsersRecyclerView.prepareForScrollParent(this)
        binding.browsersRecyclerView.adapter = adapter

        setupFeatureSwitch()
    }

    override fun onResume() {
        super.onResume()
        refreshAccessibilityStatus()
        refreshFeatureSwitch()
        refreshBrowsers()
    }

    private fun setupFeatureSwitch() {
        binding.incognitoEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            InAppFeatureSettings.setBrowserIncognitoBlocked(this, isChecked)
            refreshBrowsers()
        }
    }

    private fun refreshFeatureSwitch() {
        binding.incognitoEnabledSwitch.setOnCheckedChangeListener(null)
        binding.incognitoEnabledSwitch.isChecked = InAppFeatureSettings.isBrowserIncognitoBlocked(this)
        setupFeatureSwitch()
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

    private fun refreshBrowsers() {
        val enabled = InAppFeatureSettings.isBrowserIncognitoBlocked(this)
        val selectedPackages = InAppFeatureSettings.getBrowserIncognitoBlockedPackages(this)
        val rows = SupportedBrowsers.incognitoCapableBrowsers().mapNotNull { browser ->
            browser.toRow(
                blocked = browser.packageName in selectedPackages,
                controlsEnabled = enabled
            )
        }

        adapter.submitList(rows)
        binding.emptyBrowsersText.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.browsersRecyclerView.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun SupportedBrowser.toRow(
        blocked: Boolean,
        controlsEnabled: Boolean
    ): IncognitoBrowserRow? {
        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: Exception) {
            return null
        }

        val appLabel = packageManager.getApplicationLabel(appInfo).toString()
        val label = appLabel.ifBlank { fallbackLabel }
        val familyLabel = when (family) {
            BrowserFamily.CHROME -> getString(R.string.browser_chrome)
            BrowserFamily.FIREFOX -> getString(R.string.browser_firefox)
            BrowserFamily.OTHER -> fallbackLabel
        }

        return IncognitoBrowserRow(
            packageName = packageName,
            label = label,
            subtitle = getString(R.string.incognito_block_browser_subtitle, familyLabel, packageName),
            blocked = blocked,
            controlsEnabled = controlsEnabled
        )
    }
}

private data class IncognitoBrowserRow(
    val packageName: String,
    val label: String,
    val subtitle: String,
    val blocked: Boolean,
    val controlsEnabled: Boolean
)

private class IncognitoBrowserAdapter(
    private val onToggle: (String, Boolean) -> Unit
) : ListAdapter<IncognitoBrowserRow, IncognitoBrowserAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<IncognitoBrowserRow>() {
        override fun areItemsTheSame(oldItem: IncognitoBrowserRow, newItem: IncognitoBrowserRow) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: IncognitoBrowserRow, newItem: IncognitoBrowserRow) =
            oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemIncognitoBrowserBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: IncognitoBrowserRow) {
            binding.browserTitle.text = row.label
            binding.browserPackage.text = row.subtitle
            binding.browserSwitch.setOnCheckedChangeListener(null)
            binding.browserSwitch.isChecked = row.blocked
            binding.browserSwitch.isEnabled = row.controlsEnabled
            binding.browserSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(row.packageName, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIncognitoBrowserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
