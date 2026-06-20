package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var adapter: AdultDomainAdapter
    private val blocklistStore by lazy { UrlBlocklistStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdultFilterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AdultDomainAdapter { domain ->
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

        binding.domainsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.domainsRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshDomains()
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

        blocklistStore.ensureLoaded()
        binding.blocklistInfoText.text = getString(
            R.string.adult_filter_blocklist_info,
            blocklistStore.builtInCount(),
            AdultFilterSettings.getCustomDomains(this).size
        )
    }

    private fun refreshDomains() {
        val domains = AdultFilterSettings.getCustomDomains(this).sorted()
        adapter.submitList(domains)
        binding.emptyDomainsText.visibility =
            if (domains.isEmpty()) View.VISIBLE else View.GONE
        binding.blocklistInfoText.text = getString(
            R.string.adult_filter_blocklist_info,
            blocklistStore.builtInCount(),
            domains.size
        )
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
        refreshDomains()
        Toast.makeText(this, R.string.adult_filter_domain_added, Toast.LENGTH_SHORT).show()
    }
}

private class AdultDomainAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<String, AdultDomainAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemAdultDomainBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(domain: String) {
            binding.domainText.text = domain
            binding.btnRemoveDomain.setOnClickListener { onRemove(domain) }
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
