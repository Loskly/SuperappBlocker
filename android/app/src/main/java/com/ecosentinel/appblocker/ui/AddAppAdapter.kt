package com.ecosentinel.appblocker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.databinding.ItemAddAppBinding
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper

class AddAppAdapter(
    private val onClick: (InstalledApp) -> Unit
) : ListAdapter<InstalledApp, AddAppAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<InstalledApp>() {
        override fun areItemsTheSame(oldItem: InstalledApp, newItem: InstalledApp) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: InstalledApp, newItem: InstalledApp) =
            oldItem == newItem
    }

    class ViewHolder(
        private val binding: ItemAddAppBinding,
        private val onClick: (InstalledApp) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: InstalledApp) {
            binding.appNameText.text = app.label
            binding.packageText.text = app.packageName
            binding.appIcon.setImageDrawable(
                InstalledAppsHelper.getAppIcon(binding.root.context, app.packageName)
            )
            binding.root.setOnClickListener { onClick(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAddAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
