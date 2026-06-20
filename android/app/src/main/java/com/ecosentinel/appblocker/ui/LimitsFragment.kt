package com.ecosentinel.appblocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.databinding.FragmentLimitsBinding
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.CooldownSettings
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LimitsFragment : Fragment(), EditRuleDialog.Listener {

    private var _binding: FragmentLimitsBinding? = null
    private val binding get() = _binding!!

    private lateinit var ruleAdapter: RuleAdapter
    private lateinit var usageTracker: UsageTracker

    private val addAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch { usageTracker.syncTodayUsage() }
            Toast.makeText(requireContext(), R.string.rule_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private val addCategoryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch { usageTracker.syncTodayUsage() }
            Toast.makeText(requireContext(), R.string.rule_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private val addGroupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            viewLifecycleOwner.lifecycleScope.launch { usageTracker.syncTodayUsage() }
            Toast.makeText(requireContext(), R.string.rule_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private val addWebsiteLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(requireContext(), R.string.rule_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLimitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        usageTracker = UsageTracker(requireContext())

        ruleAdapter = RuleAdapter(
            onEdit = { row ->
                when (row.targetType) {
                    TargetType.CATEGORY -> EditRuleDialog.newInstanceForEdit(
                        ruleId = row.id,
                        targetLabel = row.appLabel,
                        targetType = TargetType.CATEGORY,
                        categoryId = row.categoryId.orEmpty()
                    ).show(parentFragmentManager, "edit_rule")
                    TargetType.CUSTOM_GROUP -> EditRuleDialog.newInstanceForEdit(
                        ruleId = row.id,
                        targetLabel = row.appLabel,
                        targetType = TargetType.CUSTOM_GROUP,
                        groupId = row.groupId.orEmpty()
                    ).show(parentFragmentManager, "edit_rule")
                    TargetType.URL_PATTERN -> EditRuleDialog.newInstanceForEdit(
                        ruleId = row.id,
                        targetLabel = row.appLabel,
                        targetType = TargetType.URL_PATTERN,
                        websiteDomain = row.websiteDomain.orEmpty()
                    ).show(parentFragmentManager, "edit_rule")
                    else -> EditRuleDialog.newInstanceForEdit(
                        ruleId = row.id,
                        targetLabel = row.appLabel,
                        targetType = TargetType.APP,
                        packageName = row.packageName
                    ).show(parentFragmentManager, "edit_rule")
                }
            },
            onToggle = { row, enabled ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = AppDatabase.getInstance(requireContext()).policyRuleDao()
                    val rule = dao.getById(row.id) ?: return@launch
                    dao.upsert(rule.copy(enabled = enabled))
                }
            },
            onDelete = { row ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_rule)
                    .setMessage(getString(R.string.delete_rule_confirm, row.appLabel))
                    .setPositiveButton(R.string.delete_rule) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            AppDatabase.getInstance(requireContext()).policyRuleDao().deleteById(row.id)
                            CooldownManager(requireContext()).clearForRule(row.id)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )

        binding.rulesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.rulesRecyclerView.adapter = ruleAdapter

        binding.btnAddApp.setOnClickListener {
            addAppLauncher.launch(Intent(requireContext(), AddAppActivity::class.java))
        }

        binding.btnAddCategory.setOnClickListener {
            addCategoryLauncher.launch(Intent(requireContext(), AddCategoryActivity::class.java))
        }

        binding.btnAddGroup.setOnClickListener {
            addGroupLauncher.launch(Intent(requireContext(), AddGroupActivity::class.java))
        }

        binding.btnAddWebsite.setOnClickListener {
            addWebsiteLauncher.launch(Intent(requireContext(), AddWebsiteActivity::class.java))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            observeRules()
        }
    }

    override fun onRuleSaved() {
        Toast.makeText(requireContext(), R.string.rule_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun observeRules() {
        val context = requireContext()
        val dao = AppDatabase.getInstance(context).policyRuleDao()
        val usageDao = AppDatabase.getInstance(context).usageDailyDao()

        dao.observeAll().collectLatest { rules ->
            val usage = withContext(Dispatchers.IO) {
                usageTracker.syncTodayUsage()
                AppGroupHelper.ensureCacheLoaded(context)
                usageDao.getForDate(usageTracker.todayKey())
                    .associate { it.packageName to it.usedMillis }
            }

            val rows = withContext(Dispatchers.Default) {
                rules.mapNotNull { rule -> ruleToRow(context, rule, usage) }
                    .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.blockMode.name }))
            }

            ruleAdapter.submitList(rows)
            binding.emptyRulesText.visibility =
                if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun ruleToRow(
        context: android.content.Context,
        rule: PolicyRuleEntity,
        usage: Map<String, Long>
    ): RuleRow? {
        if (rule.moduleType != BlockModuleType.APP_LIMIT) {
            return null
        }

        val isCategory = rule.targetType == TargetType.CATEGORY || rule.id.startsWith("category:")
        val isGroup = rule.targetType == TargetType.CUSTOM_GROUP || rule.id.startsWith("group:")
        val isWebsite = rule.targetType == TargetType.URL_PATTERN || rule.id.startsWith("site:")
        return when {
            isWebsite -> {
                val domain = rule.featureId?.let { com.ecosentinel.appblocker.modules.adult.AdultFilterSettings.normalizeDomain(it) }
                    ?: return null
                RuleRow(
                    id = rule.id,
                    targetType = TargetType.URL_PATTERN,
                    packageName = "",
                    categoryId = null,
                    groupId = null,
                    websiteDomain = domain,
                    appLabel = domain,
                    blockMode = rule.blockMode,
                    dailyLimitMinutes = null,
                    schedule = BlockSchedule.fromJson(rule.scheduleJson),
                    cooldownSettings = null,
                    usedMillis = 0L,
                    enabled = rule.enabled
                )
            }
            isGroup -> {
                val groupId = rule.featureId?.takeIf { it.isNotBlank() } ?: return null
                RuleRow(
                    id = rule.id,
                    targetType = TargetType.CUSTOM_GROUP,
                    packageName = "",
                    categoryId = null,
                    groupId = groupId,
                    websiteDomain = null,
                    appLabel = AppGroupHelper.displayNameForGroupId(groupId),
                    blockMode = rule.blockMode,
                    dailyLimitMinutes = rule.dailyLimitMinutes,
                    schedule = BlockSchedule.fromJson(rule.scheduleJson),
                    cooldownSettings = CooldownSettings.fromJson(rule.metadataJson),
                    usedMillis = AppGroupHelper.aggregateUsageForGroup(usage, groupId, context.packageName),
                    enabled = rule.enabled
                )
            }
            isCategory -> {
                val categoryId = rule.featureId?.takeIf { it.isNotBlank() }
                    ?: return null
                RuleRow(
                    id = rule.id,
                    targetType = TargetType.CATEGORY,
                    packageName = "",
                    categoryId = categoryId,
                    groupId = null,
                    websiteDomain = null,
                    appLabel = AppCategoryHelper.displayNameForCategoryId(categoryId),
                    blockMode = rule.blockMode,
                    dailyLimitMinutes = rule.dailyLimitMinutes,
                    schedule = BlockSchedule.fromJson(rule.scheduleJson),
                    cooldownSettings = CooldownSettings.fromJson(rule.metadataJson),
                    usedMillis = AppCategoryHelper.aggregateUsageForCategory(context, usage, categoryId),
                    enabled = rule.enabled
                )
            }
            else -> {
                val pkg = rule.packageName?.takeIf { it.isNotBlank() } ?: return null
                RuleRow(
                    id = rule.id,
                    targetType = TargetType.APP,
                    packageName = pkg,
                    categoryId = null,
                    groupId = null,
                    websiteDomain = null,
                    appLabel = InstalledAppsHelper.getAppLabel(context, pkg),
                    blockMode = rule.blockMode,
                    dailyLimitMinutes = rule.dailyLimitMinutes,
                    schedule = BlockSchedule.fromJson(rule.scheduleJson),
                    cooldownSettings = CooldownSettings.fromJson(rule.metadataJson),
                    usedMillis = usage[pkg] ?: 0L,
                    enabled = rule.enabled
                )
            }
        }
    }
}
