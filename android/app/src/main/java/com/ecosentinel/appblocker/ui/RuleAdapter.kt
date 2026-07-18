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
import com.ecosentinel.appblocker.engine.RuleLockMode
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.util.InstalledAppsHelper

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
    val enabled: Boolean,
    val lockMode: RuleLockMode,
    val strictLocked: Boolean,
    val blockActive: Boolean
)

enum class StrictRuleAction {
    EDIT,
    DISABLE,
    DELETE
}

class RuleAdapter(
    private val onEdit: (RuleRow) -> Unit,
    private val onToggle: (RuleRow, Boolean) -> Unit,
    private val onDelete: (RuleRow) -> Unit,
    private val onStrictAction: (RuleRow, StrictRuleAction) -> Unit
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
            val isStrict = row.lockMode == RuleLockMode.STRICT

            binding.appNameText.text = row.appLabel
            var badgeText: String? = null
            when {
                isWebsite -> {
                    binding.ruleIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_website_rule)
                    )
                    binding.packageText.text = context.getString(R.string.rule_type_website)
                    badgeText = context.getString(R.string.rule_website_badge)
                }
                isGroup -> {
                    binding.ruleIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_group_rule)
                    )
                    binding.packageText.text = context.getString(R.string.rule_type_group)
                    badgeText = context.getString(R.string.rule_group_badge)
                }
                isCategory -> {
                    binding.ruleIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_category_rule)
                    )
                    binding.packageText.text = context.getString(R.string.rule_type_category)
                    badgeText = context.getString(R.string.rule_category_badge)
                }
                else -> {
                    binding.ruleIcon.setImageDrawable(
                        InstalledAppsHelper.getAppIcon(context, row.packageName)
                    )
                    binding.packageText.text = row.packageName
                }
            }

            val strictBadge = context.getString(R.string.rule_lock_badge)
            val combinedBadge = when {
                isStrict && badgeText != null -> "$badgeText / $strictBadge"
                isStrict -> strictBadge
                else -> badgeText
            }
            binding.categoryBadge.text = combinedBadge.orEmpty()
            binding.categoryBadge.visibility = if (combinedBadge != null) View.VISIBLE else View.GONE
            binding.categoryBadge.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isStrict) R.color.block_accent else R.color.neon_secondary
                )
            )

            val usedText = UsageTracker.formatDurationStatic(row.usedMillis)
            binding.usageText.text = when {
                isWebsite -> context.getString(R.string.rule_website_usage)
                isGroup -> context.getString(R.string.rule_group_usage_today, usedText)
                isCategory -> context.getString(R.string.rule_category_usage_today, usedText)
                else -> context.getString(R.string.rule_app_usage_today, usedText)
            }

            val modeText = when (row.blockMode) {
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
            binding.modeText.text = if (isStrict) {
                "$strictBadge / $modeText"
            } else {
                modeText
            }

            binding.enabledSwitch.setOnCheckedChangeListener(null)
            binding.enabledSwitch.setOnClickListener(null)
            binding.enabledSwitch.isChecked = row.enabled
            binding.enabledSwitch.alpha = if (row.strictLocked && row.enabled) 0.65f else 1f
            binding.enabledSwitch.setOnClickListener {
                val desiredEnabled = binding.enabledSwitch.isChecked
                if (!desiredEnabled && row.strictLocked) {
                    binding.enabledSwitch.isChecked = row.enabled
                    onStrictAction(row, StrictRuleAction.DISABLE)
                } else {
                    onToggle(row, desiredEnabled)
                }
            }

            binding.btnEdit.setOnClickListener {
                if (row.strictLocked) {
                    onStrictAction(row, StrictRuleAction.EDIT)
                } else {
                    onEdit(row)
                }
            }
            binding.btnDelete.setOnClickListener {
                if (row.strictLocked) {
                    onStrictAction(row, StrictRuleAction.DELETE)
                } else {
                    onDelete(row)
                }
            }
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
