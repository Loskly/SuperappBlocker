package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.AppGroupEntity
import com.ecosentinel.appblocker.data.entity.PolicyRuleEntity
import com.ecosentinel.appblocker.databinding.ActivityAddGroupBinding
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.RuleLockPolicy
import com.ecosentinel.appblocker.engine.RuleLockReason
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AddGroupActivity : AppCompatActivity(), EditRuleDialog.Listener {

    private lateinit var binding: ActivityAddGroupBinding
    private lateinit var groupsAdapter: AddGroupAdapter
    private lateinit var appsAdapter: GroupAppPickAdapter

    private var editingGroupId: String? = null
    private var allApps: List<InstalledApp> = emptyList()
    private val selectedPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddGroupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupsAdapter = AddGroupAdapter(
            onGroupClick = { group ->
                EditRuleDialog.newInstanceForGroup(group.id, group.name)
                    .show(supportFragmentManager, "edit_rule")
            },
            onEditGroup = { group -> showEditPanel(group.id) }
        )

        appsAdapter = GroupAppPickAdapter(selectedPackages) { packageName, checked ->
            if (checked) {
                selectedPackages.add(packageName)
            } else {
                selectedPackages.remove(packageName)
            }
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCreateGroup.setOnClickListener { showEditPanel(null) }
        binding.btnEditBack.setOnClickListener { showListPanel() }
        binding.btnSave.setOnClickListener { saveGroup() }
        binding.btnDeleteGroup.setOnClickListener { confirmDeleteGroup() }

        binding.groupsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.groupsRecyclerView.adapter = groupsAdapter

        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsRecyclerView.adapter = appsAdapter

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString().orEmpty())
            }
        })

        lifecycleScope.launch {
            AppDatabase.getInstance(this@AddGroupActivity)
                .appGroupDao()
                .observeAll()
                .collectLatest { groups ->
                    val rows = withContext(Dispatchers.IO) {
                        groups.map { group -> groupToRow(group) }
                    }
                    groupsAdapter.submitList(rows)
                    binding.emptyGroupsText.visibility =
                        if (rows.isEmpty()) View.VISIBLE else View.GONE
                }
        }

        loadApps()
    }

    override fun onRuleSaved() {
        setResult(RESULT_OK)
        showListPanel()
    }

    private fun showListPanel() {
        editingGroupId = null
        binding.listPanel.visibility = View.VISIBLE
        binding.editPanel.visibility = View.GONE
    }

    private fun showEditPanel(groupId: String?) {
        editingGroupId = groupId
        val isEditing = groupId != null

        binding.listPanel.visibility = View.GONE
        binding.editPanel.visibility = View.VISIBLE

        binding.screenTitle.text = getString(
            if (isEditing) R.string.edit_group else R.string.create_group
        )
        binding.btnDeleteGroup.visibility = if (isEditing) View.VISIBLE else View.GONE
        binding.groupNameInput.setText("")
        binding.searchInput.setText("")
        selectedPackages.clear()
        appsAdapter.notifyDataSetChanged()

        if (isEditing) {
            loadExistingGroup(groupId!!)
        } else {
            applyFilter("")
        }
    }

    private fun loadExistingGroup(groupId: String) {
        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AddGroupActivity).appGroupDao()
            val group = withContext(Dispatchers.IO) { dao.getById(groupId) } ?: run {
                showListPanel()
                return@launch
            }
            binding.groupNameInput.setText(group.name)
            selectedPackages.clear()
            selectedPackages.addAll(withContext(Dispatchers.IO) { dao.getMemberPackages(groupId) })
            appsAdapter.notifyDataSetChanged()
            applyFilter(binding.searchInput.text?.toString().orEmpty())
        }
    }

    private fun loadApps() {
        binding.loadingText.visibility = View.VISIBLE
        binding.emptyAppsText.visibility = View.GONE

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) {
                InstalledAppsHelper.getAllInstalledApps(this@AddGroupActivity)
            }
            binding.loadingText.visibility = View.GONE
            if (allApps.isEmpty()) {
                binding.emptyAppsText.visibility = View.VISIBLE
            } else if (binding.editPanel.visibility == View.VISIBLE) {
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
        val sorted = filtered.sortedForGroupPicker()
        appsAdapter.submitList(sorted)
        binding.emptyAppsText.visibility =
            if (sorted.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun List<InstalledApp>.sortedForGroupPicker(): List<InstalledApp> {
        return sortedWith(
            compareBy<InstalledApp> { it.packageName !in selectedPackages }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName.lowercase() }
        )
    }

    private fun saveGroup() {
        val name = binding.groupNameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.group_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.group_apps_required, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val id = editingGroupId ?: UUID.randomUUID().toString()
            val dao = AppDatabase.getInstance(this@AddGroupActivity).appGroupDao()
            val isNewGroup = editingGroupId == null

            if (!isNewGroup) {
                val strictMessage = withContext(Dispatchers.IO) {
                    lockedGroupRuleMessage(id)
                }
                if (strictMessage != null) {
                    Toast.makeText(this@AddGroupActivity, strictMessage, Toast.LENGTH_LONG).show()
                    return@launch
                }
            }

            withContext(Dispatchers.IO) {
                val existing = if (!isNewGroup) dao.getById(id) else null
                dao.upsert(
                    AppGroupEntity(
                        id = id,
                        name = name,
                        createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis()
                    )
                )
                AppGroupHelper.saveGroupMembers(this@AddGroupActivity, id, selectedPackages.toSet())
            }

            editingGroupId = id
            setResult(RESULT_OK)

            if (isNewGroup) {
                EditRuleDialog.newInstanceForGroup(id, name)
                    .show(supportFragmentManager, "edit_rule")
            } else {
                Toast.makeText(this@AddGroupActivity, R.string.group_saved, Toast.LENGTH_SHORT).show()
                showListPanel()
            }
        }
    }

    private fun confirmDeleteGroup() {
        val id = editingGroupId ?: return
        val name = binding.groupNameInput.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            val strictMessage = withContext(Dispatchers.IO) {
                lockedGroupRuleMessage(id)
            }
            if (strictMessage != null) {
                Toast.makeText(this@AddGroupActivity, strictMessage, Toast.LENGTH_LONG).show()
                return@launch
            }

            MaterialAlertDialogBuilder(this@AddGroupActivity)
                .setTitle(R.string.delete_group)
                .setMessage(getString(R.string.delete_group_confirm, name))
                .setPositiveButton(R.string.delete_group) { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val db = AppDatabase.getInstance(this@AddGroupActivity)
                            val ruleDao = db.policyRuleDao()
                            val rules = ruleDao.getAllByGroupId(id)
                            rules.forEach { rule ->
                                ruleDao.deleteById(rule.id)
                                CooldownManager(this@AddGroupActivity).clearForRule(rule.id)
                            }
                            db.appGroupDao().deleteMembersForGroup(id)
                            db.appGroupDao().deleteById(id)
                            AppGroupHelper.onGroupsChanged(this@AddGroupActivity)
                        }
                        setResult(RESULT_OK)
                        showListPanel()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private suspend fun lockedGroupRuleMessage(groupId: String): String? {
        val db = AppDatabase.getInstance(this@AddGroupActivity)
        val ruleDao = db.policyRuleDao()
        val rules = ruleDao.getAllByGroupId(groupId)
        if (rules.isEmpty()) {
            return null
        }

        val nowMillis = System.currentTimeMillis()
        val usage = UsageTracker(this@AddGroupActivity).syncTodayUsage()
        val activeCooldownRuleIds = db.cooldownStateDao()
            .getActiveRuleIds(nowMillis)
            .toSet()

        for (rule in rules) {
            val usedMillis = AppGroupHelper.aggregateUsageForGroup(
                usage,
                groupId,
                packageName
            )
            val blockActive = isRuleBlockActive(
                rule = rule,
                usedMillis = usedMillis,
                activeCooldownRuleIds = activeCooldownRuleIds
            )

            val delayStarted = RuleLockPolicy.shouldStartDelay(rule)
            val checkedRule = if (delayStarted) {
                rule.copy(lockDelayStartedAtMillis = nowMillis).also { ruleDao.upsert(it) }
            } else {
                rule
            }
            val state = RuleLockPolicy.evaluate(
                rule = checkedRule,
                nowMillis = nowMillis,
                isBlockActive = blockActive
            )
            if (state.locked) {
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
                return if (delayStarted) {
                    "${getString(R.string.rule_lock_delay_started)} $statusText"
                } else {
                    statusText
                }
            }
        }

        return null
    }

    private fun isRuleBlockActive(
        rule: PolicyRuleEntity,
        usedMillis: Long,
        activeCooldownRuleIds: Set<String>
    ): Boolean {
        return when (rule.blockMode) {
            BlockMode.PERMANENT -> true
            BlockMode.TIME_LIMIT -> {
                val limitMinutes = rule.dailyLimitMinutes ?: return false
                usedMillis >= limitMinutes * 60_000L
            }
            BlockMode.TIME_OF_DAY -> BlockSchedule.fromJson(rule.scheduleJson)?.isActiveNow() == true
            BlockMode.COOLDOWN -> rule.id in activeCooldownRuleIds
        }
    }

    private suspend fun groupToRow(group: AppGroupEntity): GroupRow {
        val count = AppDatabase.getInstance(this).appGroupDao().getMemberCount(group.id)
        return GroupRow(
            id = group.id,
            name = group.name,
            appCount = count
        )
    }
}

data class GroupRow(
    val id: String,
    val name: String,
    val appCount: Int
)
