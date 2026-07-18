package com.ecosentinel.appblocker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.databinding.ItemFocusAppPickBinding
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper

class GroupAppPickAdapter(
    private val selectedPackages: Set<String>,
    private val onCheckedChanged: (packageName: String, checked: Boolean) -> Unit
) : ListAdapter<InstalledApp, GroupAppPickAdapter.ViewHolder>(Diff) {

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
