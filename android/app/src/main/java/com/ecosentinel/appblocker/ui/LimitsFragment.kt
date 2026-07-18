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
import com.ecosentinel.appblocker.engine.RuleLockPolicy
import com.ecosentinel.appblocker.engine.RuleLockReason
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

private data class RuleUsageSnapshot(
    val usage: Map<String, Long>,
    val activeCooldownRuleIds: Set<String>
)

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
                openEditDialog(row)
            },
            onToggle = { row, enabled ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val dao = AppDatabase.getInstance(requireContext()).policyRuleDao()
                    val rule = dao.getById(row.id) ?: return@launch
                    dao.upsert(rule.copy(enabled = enabled))
                }
            },
            onDelete = { row ->
                confirmDeleteRule(row)
            },
            onStrictAction = { row, action ->
                handleStrictAction(row, action)
            }
        )

        binding.rulesRecyclerView.prepareForScrollParent(requireContext())
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

    private fun openEditDialog(row: RuleRow) {
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
    }

    private fun confirmDeleteRule(row: RuleRow) {
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

    private fun handleStrictAction(row: RuleRow, action: StrictRuleAction) {
        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.getInstance(requireContext()).policyRuleDao()
            val nowMillis = System.currentTimeMillis()
            val rule = dao.getById(row.id) ?: return@launch
            val delayStarted = RuleLockPolicy.shouldStartDelay(rule)
            val checkedRule = if (delayStarted) {
                rule.copy(lockDelayStartedAtMillis = nowMillis).also { dao.upsert(it) }
            } else {
                rule
            }
            val state = RuleLockPolicy.evaluate(
                rule = checkedRule,
                nowMillis = nowMillis,
                isBlockActive = row.blockActive
            )

            if (!state.locked) {
                when (action) {
                    StrictRuleAction.EDIT -> openEditDialog(row)
                    StrictRuleAction.DISABLE -> dao.upsert(checkedRule.copy(enabled = false))
                    StrictRuleAction.DELETE -> confirmDeleteRule(row)
                }
                return@launch
            }

            val statusText = when (state.reason) {
                RuleLockReason.UNTIL_TIME -> getString(
                    R.string.rule_lock_status_until,
                    BlockSchedule.formatDurationUntil(state.remainingMillis)
                )
                RuleLockReason.BLOCK_ACTIVE -> getString(R.string.rule_lock_status_block_active)
                RuleLockReason.DELAY_NOT_STARTED -> getString(
                    R.string.rule_lock_status_delay_not_started,
                    BlockSchedule.formatDurationUntil(state.remainingMillis)
                )
                RuleLockReason.DELAY_WAITING -> getString(
                    R.string.rule_lock_status_delay_waiting,
                    BlockSchedule.formatDurationUntil(state.remainingMillis)
                )
                RuleLockReason.STRICT_WITHOUT_CONDITION -> getString(R.string.rule_lock_status_no_condition)
                RuleLockReason.NONE -> getString(R.string.rule_lock_status_no_condition)
            }

            val message = if (delayStarted) {
                "${getString(R.string.rule_lock_delay_started)} $statusText"
            } else {
                statusText
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
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
            val nowMillis = System.currentTimeMillis()
            val snapshot = withContext(Dispatchers.IO) {
                usageTracker.syncTodayUsage()
                AppGroupHelper.ensureCacheLoaded(context)
                val usage = usageDao.getForDate(usageTracker.todayKey())
                    .associate { it.packageName to it.usedMillis }
                val activeCooldownRuleIds = AppDatabase.getInstance(context)
                    .cooldownStateDao()
                    .getActiveRuleIds(nowMillis)
                    .toSet()
                RuleUsageSnapshot(
                    usage = usage,
                    activeCooldownRuleIds = activeCooldownRuleIds
                )
            }

            val rows = withContext(Dispatchers.Default) {
                rules.mapNotNull { rule ->
                    ruleToRow(
                        context = context,
                        rule = rule,
                        usage = snapshot.usage,
                        activeCooldownRuleIds = snapshot.activeCooldownRuleIds,
                        nowMillis = nowMillis
                    )
                }
                    .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.blockMode.name }))
            }

            ruleAdapter.submitListRemeasure(binding.rulesRecyclerView, rows)
            binding.emptyRulesText.visibility =
                if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun ruleToRow(
        context: android.content.Context,
        rule: PolicyRuleEntity,
        usage: Map<String, Long>,
        activeCooldownRuleIds: Set<String>,
        nowMillis: Long
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
                val schedule = BlockSchedule.fromJson(rule.scheduleJson)
                val blockActive = isRuleBlockActive(rule, 0L, activeCooldownRuleIds, schedule)
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
                    schedule = schedule,
                    cooldownSettings = null,
                    usedMillis = 0L,
                    enabled = rule.enabled,
                    lockMode = rule.lockMode,
                    strictLocked = RuleLockPolicy.evaluate(rule, nowMillis, blockActive).locked,
                    blockActive = blockActive
                )
            }
            isGroup -> {
                val groupId = rule.featureId?.takeIf { it.isNotBlank() } ?: return null
                val usedMillis = AppGroupHelper.aggregateUsageForGroup(usage, groupId, context.packageName)
                val schedule = BlockSchedule.fromJson(rule.scheduleJson)
                val blockActive = isRuleBlockActive(rule, usedMillis, activeCooldownRuleIds, schedule)
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
                    schedule = schedule,
                    cooldownSettings = CooldownSettings.fromJson(rule.metadataJson),
                    usedMillis = usedMillis,
                    enabled = rule.enabled,
                    lockMode = rule.lockMode,
                    strictLocked = RuleLockPolicy.evaluate(rule, nowMillis, blockActive).locked,
                    blockActive = blockActive
                )
            }
            isCategory -> {
                val categoryId = rule.featureId?.takeIf { it.isNotBlank() }
                    ?: return null
                val usedMillis = AppCategoryHelper.aggregateUsageForCategory(context, usage, categoryId)
                val schedule = BlockSchedule.fromJson(rule.scheduleJson)
                val blockActive = isRuleBlockActive(rule, usedMillis, activeCooldownRuleIds, schedule)
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
                    schedule = schedule,
                    cooldownSettings = CooldownSettings.fromJson(rule.metadataJson),
                    usedMillis = usedMillis,
                    enabled = rule.enabled,
                    lockMode = rule.lockMode,
                    strictLocked = RuleLockPolicy.evaluate(rule, nowMillis, blockActive).locked,
                    blockActive = blockActive
                )
            }
            else -> {
                val pkg = rule.packageName?.takeIf { it.isNotBlank() } ?: return null
                val usedMillis = usage[pkg] ?: 0L
                val schedule = BlockSchedule.fromJson(rule.scheduleJson)
                val blockActive = isRuleBlockActive(rule, usedMillis, activeCooldownRuleIds, schedule)
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
                    schedule = schedule,
                    cooldownSettings = CooldownSettings.fromJson(rule.metadataJson),
                    usedMillis = usedMillis,
                    enabled = rule.enabled,
                    lockMode = rule.lockMode,
                    strictLocked = RuleLockPolicy.evaluate(rule, nowMillis, blockActive).locked,
                    blockActive = blockActive
                )
            }
        }
    }

    private fun isRuleBlockActive(
        rule: PolicyRuleEntity,
        usedMillis: Long,
        activeCooldownRuleIds: Set<String>,
        schedule: BlockSchedule?
    ): Boolean {
        return when (rule.blockMode) {
            com.ecosentinel.appblocker.engine.BlockMode.PERMANENT -> true
            com.ecosentinel.appblocker.engine.BlockMode.TIME_LIMIT -> {
                val limitMinutes = rule.dailyLimitMinutes ?: return false
                usedMillis >= limitMinutes * 60_000L
            }
            com.ecosentinel.appblocker.engine.BlockMode.TIME_OF_DAY -> schedule?.isActiveNow() == true
            com.ecosentinel.appblocker.engine.BlockMode.COOLDOWN -> rule.id in activeCooldownRuleIds
        }
    }
}
