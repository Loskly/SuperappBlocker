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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.cooldown.CooldownManager
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.AppGroupEntity
import com.ecosentinel.appblocker.databinding.ActivityAddGroupBinding
import com.ecosentinel.appblocker.databinding.ItemFocusAppPickBinding
import com.ecosentinel.appblocker.tracker.AppGroupHelper
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
        appsAdapter.submitList(filtered)
        binding.emptyAppsText.visibility =
            if (filtered.isEmpty() && allApps.isNotEmpty()) View.VISIBLE else View.GONE
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
        MaterialAlertDialogBuilder(this)
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

private class GroupAppPickAdapter(
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
