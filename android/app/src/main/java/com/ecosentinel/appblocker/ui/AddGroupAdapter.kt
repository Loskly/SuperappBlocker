package com.ecosentinel.appblocker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ItemGroupBinding

class AddGroupAdapter(
    private val onGroupClick: (GroupRow) -> Unit,
    private val onEditGroup: (GroupRow) -> Unit
) : ListAdapter<GroupRow, AddGroupAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<GroupRow>() {
        override fun areItemsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GroupRow, newItem: GroupRow) = oldItem == newItem
    }

    inner class ViewHolder(private val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: GroupRow) {
            val context = binding.root.context
            binding.groupNameText.text = row.name
            binding.groupAppsCountText.text = context.resources.getQuantityString(
                R.plurals.group_apps_count,
                row.appCount,
                row.appCount
            )
            binding.root.setOnClickListener { onGroupClick(row) }
            binding.btnEditGroup.setOnClickListener { onEditGroup(row) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
