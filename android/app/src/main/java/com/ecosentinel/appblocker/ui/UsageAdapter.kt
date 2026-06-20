package com.ecosentinel.appblocker.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.databinding.ItemUsageBinding
import java.util.concurrent.TimeUnit

data class UsageRow(
    val packageName: String,
    val appLabel: String,
    val usedMillis: Long,
    val limitMinutes: Int?
)

class UsageAdapter : ListAdapter<UsageRow, UsageAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<UsageRow>() {
        override fun areItemsTheSame(oldItem: UsageRow, newItem: UsageRow): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: UsageRow, newItem: UsageRow): Boolean {
            return oldItem == newItem
        }
    }

    class ViewHolder(private val binding: ItemUsageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: UsageRow) {
            binding.appNameText.text = row.appLabel
            val usedMinutes = TimeUnit.MILLISECONDS.toMinutes(row.usedMillis)
            val limitText = row.limitMinutes?.let { "$usedMinutes / $it мин" } ?: "$usedMinutes мин"
            binding.usageText.text = limitText
            binding.limitText.text = row.limitMinutes?.let { "Лимит: $it мин/день" } ?: "Лимит не задан"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
