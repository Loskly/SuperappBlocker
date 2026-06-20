package com.ecosentinel.appblocker.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ItemRuleBinding
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.CooldownSettings
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import java.util.concurrent.TimeUnit

data class RuleRow(
    val id: String,
    val targetType: TargetType,
    val packageName: String,
    val categoryId: String?,
    val groupId: String?,
    val websiteDomain: String?,
    val appLabel: String,
    val blockMode: BlockMode,
    val dailyLimitMinutes: Int?,
    val schedule: BlockSchedule?,
    val cooldownSettings: CooldownSettings?,
    val usedMillis: Long,
    val enabled: Boolean
)

class RuleAdapter(
    private val onEdit: (RuleRow) -> Unit,
    private val onToggle: (RuleRow, Boolean) -> Unit,
    private val onDelete: (RuleRow) -> Unit
) : ListAdapter<RuleRow, RuleAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<RuleRow>() {
        override fun areItemsTheSame(oldItem: RuleRow, newItem: RuleRow) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RuleRow, newItem: RuleRow) = oldItem == newItem
    }

    inner class ViewHolder(private val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: RuleRow) {
            val context = binding.root.context
            val isCategory = row.targetType == TargetType.CATEGORY
            val isGroup = row.targetType == TargetType.CUSTOM_GROUP
            val isWebsite = row.targetType == TargetType.URL_PATTERN

            binding.appNameText.text = row.appLabel
            when {
                isWebsite -> {
                    binding.ruleIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_website_rule)
                    )
                    binding.packageText.text = context.getString(R.string.rule_type_website)
                    binding.categoryBadge.text = context.getString(R.string.rule_website_badge)
                    binding.categoryBadge.visibility = View.VISIBLE
                }
                isGroup -> {
                    binding.ruleIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_group_rule)
                    )
                    binding.packageText.text = context.getString(R.string.rule_type_group)
                    binding.categoryBadge.text = context.getString(R.string.rule_group_badge)
                    binding.categoryBadge.visibility = View.VISIBLE
                }
                isCategory -> {
                    binding.ruleIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_category_rule)
                    )
                    binding.packageText.text = context.getString(R.string.rule_type_category)
                    binding.categoryBadge.text = context.getString(R.string.rule_category_badge)
                    binding.categoryBadge.visibility = View.VISIBLE
                }
                else -> {
                    binding.ruleIcon.setImageDrawable(
                        InstalledAppsHelper.getAppIcon(context, row.packageName)
                    )
                    binding.packageText.text = row.packageName
                    binding.categoryBadge.visibility = View.GONE
                }
            }

            val usedMinutes = TimeUnit.MILLISECONDS.toMinutes(row.usedMillis)
            binding.usageText.text = when {
                isWebsite -> context.getString(R.string.rule_website_usage)
                isGroup -> context.getString(R.string.rule_group_usage_today, usedMinutes)
                isCategory -> context.getString(R.string.rule_category_usage_today, usedMinutes)
                else -> context.getString(R.string.rule_app_usage_today, usedMinutes)
            }

            binding.modeText.text = when (row.blockMode) {
                BlockMode.PERMANENT -> context.getString(R.string.mode_permanent)
                BlockMode.TIME_LIMIT -> context.getString(
                    R.string.rule_mode_time_limit,
                    row.dailyLimitMinutes ?: 0
                )
                BlockMode.TIME_OF_DAY -> row.schedule?.formatRange()
                    ?: context.getString(R.string.mode_time_of_day)
                BlockMode.COOLDOWN -> {
                    val settings = row.cooldownSettings ?: CooldownSettings.DEFAULT
                    context.getString(
                        R.string.rule_mode_cooldown,
                        settings.usageMinutes,
                        settings.windowMinutes,
                        settings.blockMinutes
                    )
                }
            }

            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.isChecked = row.enabled
            binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(row, isChecked)
            }

            binding.btnEdit.setOnClickListener { onEdit(row) }
            binding.btnDelete.setOnClickListener { onDelete(row) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
