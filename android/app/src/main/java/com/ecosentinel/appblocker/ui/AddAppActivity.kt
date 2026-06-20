package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityAddAppBinding
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddAppActivity : AppCompatActivity(), EditRuleDialog.Listener {

    private lateinit var binding: ActivityAddAppBinding
    private lateinit var adapter: AddAppAdapter
    private var allApps: List<InstalledApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        adapter = AddAppAdapter { app ->
            EditRuleDialog.newInstance(app.packageName, app.label)
                .show(supportFragmentManager, "edit_rule")
        }
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsRecyclerView.adapter = adapter
        binding.appsRecyclerView.setHasFixedSize(false)

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })

        loadApps()
    }

    private fun loadApps() {
        binding.loadingText.visibility = View.VISIBLE
        binding.emptyAppsText.visibility = View.GONE

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) {
                InstalledAppsHelper.getAllInstalledApps(this@AddAppActivity)
            }
            binding.loadingText.visibility = View.GONE
            if (allApps.isEmpty()) {
                binding.emptyAppsText.visibility = View.VISIBLE
            } else {
                binding.emptyAppsText.visibility = View.GONE
                binding.screenTitle.text = getString(R.string.apps_list_title, allApps.size)
                applyFilter(binding.searchInput.text?.toString().orEmpty())
            }
        }
    }

    private fun applyFilter(queryRaw: String) {
        val query = queryRaw.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        adapter.submitList(filtered)
        binding.emptyAppsText.visibility =
            if (filtered.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onRuleSaved() {
        setResult(RESULT_OK)
        finish()
    }
}