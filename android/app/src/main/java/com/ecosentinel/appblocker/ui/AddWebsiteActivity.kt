package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityAddWebsiteBinding
import com.ecosentinel.appblocker.databinding.ItemWebsiteExampleBinding
import com.ecosentinel.appblocker.modules.adult.AdultFilterSettings
import com.ecosentinel.appblocker.util.PermissionHelper

class AddWebsiteActivity : AppCompatActivity(), EditRuleDialog.Listener {

    private lateinit var binding: ActivityAddWebsiteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddWebsiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener { openRuleDialog(binding.domainInput.text?.toString().orEmpty()) }

        val adapter = WebsiteExampleAdapter { domain ->
            binding.domainInput.setText(domain)
            openRuleDialog(domain)
        }
        binding.examplesRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.examplesRecyclerView.adapter = adapter
        adapter.submitList(WEBSITE_EXAMPLES)
    }

    override fun onResume() {
        super.onResume()
        val granted = PermissionHelper.isAccessibilityServiceEnabled(this)
        binding.accessibilityHintText.isVisible = !granted
        if (!granted) {
            binding.accessibilityHintText.text = getString(R.string.stayfree_accessibility_required)
        }
    }

    override fun onRuleSaved() {
        setResult(RESULT_OK)
        finish()
    }

    private fun openRuleDialog(rawDomain: String) {
        val domain = AdultFilterSettings.normalizeDomain(rawDomain)
        if (domain == null) {
            Toast.makeText(this, R.string.adult_filter_domain_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        EditRuleDialog.newInstanceForWebsite(domain)
            .show(supportFragmentManager, "edit_rule")
    }

    companion object {
        private val WEBSITE_EXAMPLES = listOf(
            "youtube.com",
            "instagram.com",
            "twitter.com",
            "reddit.com",
            "tiktok.com",
            "facebook.com"
        )
    }
}

private class WebsiteExampleAdapter(
    private val onClick: (String) -> Unit
) : ListAdapter<String, WebsiteExampleAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemWebsiteExampleBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(domain: String) {
            binding.domainText.text = domain
            binding.root.setOnClickListener { onClick(domain) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWebsiteExampleBinding.inflate(
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
