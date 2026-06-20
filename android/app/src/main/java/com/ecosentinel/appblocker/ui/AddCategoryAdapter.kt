package com.ecosentinel.appblocker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.databinding.ItemCategoryBinding
import com.ecosentinel.appblocker.tracker.AppCategory

class AddCategoryAdapter(
    private val onCategoryClick: (AppCategory) -> Unit
) : ListAdapter<AppCategory, AddCategoryAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppCategory>() {
        override fun areItemsTheSame(oldItem: AppCategory, newItem: AppCategory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppCategory, newItem: AppCategory) = oldItem == newItem
    }

    inner class ViewHolder(private val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: AppCategory) {
            binding.categoryNameText.text = category.displayName
            binding.root.setOnClickListener { onCategoryClick(category) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
