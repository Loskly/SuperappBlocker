package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.databinding.ActivityFocusPickAppsBinding
import com.ecosentinel.appblocker.databinding.ItemFocusAppPickBinding
import com.ecosentinel.appblocker.focus.FocusConfigStore
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusPickAppsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusPickAppsBinding
    private lateinit var configStore: FocusConfigStore
    private lateinit var adapter: FocusAppPickAdapter

    private var allApps: List<InstalledApp> = emptyList()
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusPickAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configStore = FocusConfigStore(this)
        selectedPackages.addAll(configStore.getSelectedPackages())

        adapter = FocusAppPickAdapter(selectedPackages) { packageName, checked ->
            if (checked) {
                selectedPackages.add(packageName)
            } else {
                selectedPackages.remove(packageName)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener {
            configStore.setSelectedPackages(selectedPackages.toSet())
            setResult(RESULT_OK)
            finish()
        }

        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsRecyclerView.adapter = adapter

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
                InstalledAppsHelper.getAllInstalledApps(this@FocusPickAppsActivity)
            }
            binding.loadingText.visibility = View.GONE
            if (allApps.isEmpty()) {
                binding.emptyAppsText.visibility = View.VISIBLE
            } else {
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
}

private class FocusAppPickAdapter(
    private val selectedPackages: Set<String>,
    private val onCheckedChanged: (packageName: String, checked: Boolean) -> Unit
) : ListAdapter<InstalledApp, FocusAppPickAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<InstalledApp>() {
        override fun areItemsTheSame(oldItem: InstalledApp, newItem: InstalledApp) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: InstalledApp, newItem: InstalledApp) =
            oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemFocusAppPickBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: InstalledApp) {
            binding.appNameText.text = app.label
            binding.packageText.text = app.packageName
            binding.appIcon.setImageDrawable(
                InstalledAppsHelper.getAppIcon(binding.root.context, app.packageName)
            )
            binding.appCheckBox.setOnCheckedChangeListener(null)
            binding.appCheckBox.isChecked = app.packageName in selectedPackages
            binding.appCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChanged(app.packageName, isChecked)
            }
            binding.root.setOnClickListener {
                binding.appCheckBox.isChecked = !binding.appCheckBox.isChecked
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFocusAppPickBinding.inflate(
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
