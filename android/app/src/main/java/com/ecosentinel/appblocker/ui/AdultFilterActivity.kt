package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityAdultFilterBinding
import com.ecosentinel.appblocker.databinding.ItemAdultDomainBinding
import com.ecosentinel.appblocker.modules.adult.AdultFilterSettings
import com.ecosentinel.appblocker.modules.adult.UrlBlocklistStore
import com.ecosentinel.appblocker.util.PermissionHelper

class AdultFilterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdultFilterBinding
    private lateinit var builtInAdapter: AdultDomainAdapter
    private lateinit var customAdapter: AdultDomainAdapter
    private val blocklistStore by lazy { UrlBlocklistStore(this) }

    private var builtInExpanded = false
    private var customExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdultFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        builtInAdapter = AdultDomainAdapter { /* built-in domains are read-only */ }
        customAdapter = AdultDomainAdapter { domain ->
            AdultFilterSettings.removeCustomDomain(this, domain)
            refreshDomains()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnGrantAccessibility.setOnClickListener {
            PermissionHelper.openAccessibilitySettings(this)
        }
        binding.btnTryBlock.setOnClickListener {
            BlockOverlayManager.show(this, "com.android.chrome", BlockOverlayActivity.REASON_ADULT_URL)
        }
        binding.btnAddDomain.setOnClickListener { addDomain() }
        binding.filterEnabledSwitch.isChecked = AdultFilterSettings.isEnabled(this)
        binding.filterEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            AdultFilterSettings.setEnabled(this, isChecked)
            refreshStatus()
        }

        binding.builtInHeader.setOnClickListener { toggleBuiltInSection() }
        binding.customHeader.setOnClickListener { toggleCustomSection() }

        setupRecyclerView(binding.builtInRecyclerView, builtInAdapter)
        setupRecyclerView(binding.customDomainsRecyclerView, customAdapter)

        if (savedInstanceState != null) {
            builtInExpanded = savedInstanceState.getBoolean(STATE_BUILTIN_EXPANDED, false)
            customExpanded = savedInstanceState.getBoolean(STATE_CUSTOM_EXPANDED, false)
        }
        applySectionExpandedState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_BUILTIN_EXPANDED, builtInExpanded)
        outState.putBoolean(STATE_CUSTOM_EXPANDED, customExpanded)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshDomains()
    }

    private fun setupRecyclerView(recyclerView: FullHeightRecyclerView, adapter: AdultDomainAdapter) {
        recyclerView.prepareForScrollParent(this)
        recyclerView.adapter = adapter
    }

    private fun toggleBuiltInSection() {
        builtInExpanded = !builtInExpanded
        applySectionExpandedState()
    }

    private fun toggleCustomSection() {
        customExpanded = !customExpanded
        applySectionExpandedState()
    }

    private fun applySectionExpandedState() {
        binding.builtInContent.visibility = if (builtInExpanded) View.VISIBLE else View.GONE
        binding.builtInExpandIcon.text = if (builtInExpanded) "▲" else "▼"

        binding.customContent.visibility = if (customExpanded) View.VISIBLE else View.GONE
        binding.customExpandIcon.text = if (customExpanded) "▲" else "▼"
    }

    private fun refreshStatus() {
        val accessibilityGranted = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.accessibilityStatusText.text = getString(
            R.string.status_line,
            getString(R.string.stayfree_accessibility_title),
            if (accessibilityGranted) getString(R.string.ok_status) else getString(R.string.no_status)
        )
        binding.btnGrantAccessibility.visibility =
            if (accessibilityGranted) View.GONE else View.VISIBLE
    }

    private fun refreshDomains() {
        blocklistStore.ensureLoaded()

        val builtInDomains = blocklistStore.getBuiltInDomains()
        val customDomains = AdultFilterSettings.getCustomDomains(this).sorted()

        builtInAdapter.submitListRemeasure(binding.builtInRecyclerView, builtInDomains.map {
            AdultDomainRow(it, deletable = false)
        })
        customAdapter.submitListRemeasure(binding.customDomainsRecyclerView, customDomains.map {
            AdultDomainRow(it, deletable = true)
        })

        binding.builtInHeaderText.text = sectionTitle(R.string.adult_filter_builtin_domains, builtInDomains.size)
        binding.customHeaderText.text = sectionTitle(R.string.adult_filter_custom_domains, customDomains.size)

        binding.emptyDomainsText.visibility =
            if (customDomains.isEmpty() && customExpanded) View.VISIBLE else View.GONE

        binding.blocklistInfoText.text = getString(
            R.string.adult_filter_blocklist_info,
            builtInDomains.size,
            customDomains.size
        )

        if (customDomains.isNotEmpty() && !customExpanded) {
            customExpanded = true
            applySectionExpandedState()
        }
    }

    private fun sectionTitle(titleRes: Int, count: Int): String {
        return getString(R.string.adult_filter_section_count, getString(titleRes), count)
    }

    private fun addDomain() {
        val raw = binding.domainInput.text?.toString().orEmpty()
        val normalized = AdultFilterSettings.normalizeDomain(raw)
        if (normalized == null) {
            Toast.makeText(this, R.string.adult_filter_domain_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        AdultFilterSettings.addCustomDomain(this, normalized)
        binding.domainInput.text?.clear()
        customExpanded = true
        refreshDomains()
        Toast.makeText(this, R.string.adult_filter_domain_added, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val STATE_BUILTIN_EXPANDED = "built_in_expanded"
        private const val STATE_CUSTOM_EXPANDED = "custom_expanded"
    }
}

private data class AdultDomainRow(
    val domain: String,
    val deletable: Boolean
)

private class AdultDomainAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<AdultDomainRow, AdultDomainAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AdultDomainRow>() {
        override fun areItemsTheSame(oldItem: AdultDomainRow, newItem: AdultDomainRow) =
            oldItem.domain == newItem.domain

        override fun areContentsTheSame(oldItem: AdultDomainRow, newItem: AdultDomainRow) =
            oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemAdultDomainBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: AdultDomainRow) {
            binding.domainText.text = row.domain
            binding.btnRemoveDomain.visibility = if (row.deletable) View.VISIBLE else View.GONE
            binding.btnRemoveDomain.setOnClickListener {
                if (row.deletable) {
                    onRemove(row.domain)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdultDomainBinding.inflate(
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
