package com.ecosentinel.appblocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityFocusBinding
import com.ecosentinel.appblocker.databinding.ItemFocusCategoryBinding
import com.ecosentinel.appblocker.focus.FocusConfigStore
import com.ecosentinel.appblocker.focus.FocusManager
import com.ecosentinel.appblocker.focus.FocusMode
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.tracker.AppCategory
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.ecosentinel.appblocker.util.PermissionHelper
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class FocusActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusBinding
    private lateinit var focusManager: FocusManager
    private lateinit var configStore: FocusConfigStore
    private lateinit var categoryAdapter: FocusCategoryAdapter

    private val selectedCategoryIds = mutableSetOf<String>()
    private var selectedPackages = setOf<String>()
    private var selectedDurationMinutes = FocusConfigStore.DEFAULT_DURATION_MINUTES
    private var tickJob: Job? = null

    private val pickAppsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            selectedPackages = configStore.getSelectedPackages()
            updateSelectedAppsSummary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusBinding.inflate(layoutInflater)
        setContentView(binding.root)

        focusManager = FocusManager(this)
        configStore = FocusConfigStore(this)

        selectedCategoryIds.addAll(configStore.getSelectedCategoryIds())
        selectedPackages = configStore.getSelectedPackages()
        selectedDurationMinutes = configStore.getDurationMinutes()

        categoryAdapter = FocusCategoryAdapter(selectedCategoryIds) { categoryId, checked ->
            if (checked) {
                selectedCategoryIds.add(categoryId)
            } else {
                selectedCategoryIds.remove(categoryId)
            }
            configStore.setSelectedCategoryIds(selectedCategoryIds.toSet())
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickApps.setOnClickListener {
            pickAppsLauncher.launch(Intent(this, FocusPickAppsActivity::class.java))
        }
        binding.btnStartFocus.setOnClickListener { startFocus() }
        binding.btnStopFocus.setOnClickListener { confirmStopFocus() }

        binding.categoriesRecyclerView.prepareForScrollParent(this)
        binding.categoriesRecyclerView.adapter = categoryAdapter
        categoryAdapter.submitList(AppCategory.selectableCategories())

        setupDurationChips()
        setupModeSelection()
        updateSelectedAppsSummary()
    }

    override fun onResume() {
        super.onResume()
        MonitorBootstrap.ensureMonitoring(this)
        refreshScreen()
    }

    override fun onPause() {
        super.onPause()
        tickJob?.cancel()
        tickJob = null
    }

    private fun setupDurationChips() {
        binding.durationChipGroup.removeAllViews()
        binding.durationChipGroup.setOnCheckedStateChangeListener(null)

        val presets = FocusConfigStore.DURATION_PRESETS
        if (selectedDurationMinutes !in presets) {
            selectedDurationMinutes = FocusConfigStore.DEFAULT_DURATION_MINUTES
        }

        presets.forEach { minutes ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(R.string.focus_duration_minutes, minutes)
                isCheckable = true
                tag = minutes
            }
            binding.durationChipGroup.addView(chip)
            if (minutes == selectedDurationMinutes) {
                binding.durationChipGroup.check(chip.id)
            }
        }

        binding.durationChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                return@setOnCheckedStateChangeListener
            }
            val chip = group.findViewById<Chip>(checkedIds.first())
            selectedDurationMinutes = chip.tag as Int
            configStore.setDurationMinutes(selectedDurationMinutes)
        }
    }

    private fun setupModeSelection() {
        binding.focusModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioStrictMode -> FocusMode.STRICT
                else -> FocusMode.SOFT
            }
            if (configStore.getMode() != mode) {
                configStore.setMode(mode)
            }
        }
    }

    private fun updateModeSelection() {
        binding.focusModeGroup.setOnCheckedChangeListener(null)
        when (configStore.getMode()) {
            FocusMode.STRICT -> binding.radioStrictMode.isChecked = true
            FocusMode.SOFT -> binding.radioSoftMode.isChecked = true
        }
        setupModeSelection()
    }

    private fun updateSelectedAppsSummary() {
        binding.selectedAppsSummaryText.text = if (selectedPackages.isEmpty()) {
            getString(R.string.focus_apps_none_selected)
        } else {
            getString(R.string.focus_apps_selected_count, selectedPackages.size)
        }
    }

    private fun refreshScreen() {
        lifecycleScope.launch {
            val session = focusManager.getActiveSession()
            if (session != null) {
                showActivePanel(session)
            } else {
                showSetupPanel()
            }
        }
    }

    private fun showSetupPanel() {
        tickJob?.cancel()
        tickJob = null

        binding.activePanel.visibility = View.GONE
        binding.setupPanel.visibility = View.VISIBLE

        selectedCategoryIds.clear()
        selectedCategoryIds.addAll(configStore.getSelectedCategoryIds())
        selectedPackages = configStore.getSelectedPackages()
        selectedDurationMinutes = configStore.getDurationMinutes()
        categoryAdapter.notifyDataSetChanged()
        setupDurationChips()
        updateModeSelection()
        updateSelectedAppsSummary()
    }

    private fun showActivePanel(session: com.ecosentinel.appblocker.data.entity.FocusSessionEntity) {
        binding.setupPanel.visibility = View.GONE
        binding.activePanel.visibility = View.VISIBLE

        binding.focusModeActiveText.text = when (session.mode) {
            FocusMode.STRICT -> getString(R.string.focus_mode_strict_active)
            FocusMode.SOFT -> getString(R.string.focus_mode_soft_active)
        }

        binding.focusBlockedSummaryText.text = buildBlockedSummary(session)

        val isSoft = session.mode == FocusMode.SOFT
        binding.btnStopFocus.visibility = if (isSoft) View.VISIBLE else View.GONE
        binding.focusStrictHintText.visibility = if (isSoft) View.GONE else View.VISIBLE

        updateRemainingTime(session)
        if (tickJob?.isActive != true) {
            tickJob = lifecycleScope.launch {
                while (isActive) {
                    delay(1_000L)
                    val active = focusManager.getActiveSession()
                    if (active == null) {
                        showSetupPanel()
                        return@launch
                    }
                    updateRemainingTime(active)
                }
            }
        }
    }

    private fun updateRemainingTime(session: com.ecosentinel.appblocker.data.entity.FocusSessionEntity) {
        val remaining = focusManager.millisUntilFocusEnds(session)
        binding.focusRemainingText.text = formatDuration(remaining)
    }

    private fun buildBlockedSummary(session: com.ecosentinel.appblocker.data.entity.FocusSessionEntity): String {
        val parts = mutableListOf<String>()
        focusManager.parseStringList(session.blockedCategoryIdsJson)
            .mapNotNull { AppCategory.fromId(it)?.displayName }
            .forEach { parts.add(it) }

        focusManager.parseStringList(session.blockedPackagesJson)
            .forEach { packageName ->
                parts.add(InstalledAppsHelper.getAppLabel(this, packageName))
            }

        return if (parts.isEmpty()) {
            getString(R.string.focus_blocked_empty)
        } else {
            getString(R.string.focus_blocked_summary, parts.joinToString(", "))
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun startFocus() {
        if (!PermissionHelper.hasUsageAccess(this)) {
            Toast.makeText(this, R.string.usage_access_required, Toast.LENGTH_LONG).show()
            return
        }
        if (!PermissionHelper.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_access_required, Toast.LENGTH_LONG).show()
            return
        }
        if (selectedCategoryIds.isEmpty() && selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.focus_select_targets, Toast.LENGTH_LONG).show()
            return
        }

        val mode = when (binding.focusModeGroup.checkedRadioButtonId) {
            R.id.radioStrictMode -> FocusMode.STRICT
            else -> FocusMode.SOFT
        }

        configStore.saveDraft(
            categoryIds = selectedCategoryIds.toSet(),
            packages = selectedPackages,
            durationMinutes = selectedDurationMinutes,
            mode = mode
        )

        lifecycleScope.launch {
            focusManager.startFocus(
                blockedCategoryIds = selectedCategoryIds.toSet(),
                blockedPackages = selectedPackages,
                durationMinutes = selectedDurationMinutes,
                mode = mode
            )
            MonitorBootstrap.ensureMonitoring(this@FocusActivity)
            Toast.makeText(this@FocusActivity, R.string.focus_started, Toast.LENGTH_SHORT).show()
            refreshScreen()
        }
    }

    private fun confirmStopFocus() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.focus_stop_confirm_title)
            .setMessage(R.string.focus_stop_confirm_message)
            .setPositiveButton(R.string.focus_stop) { _, _ -> stopFocus() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun stopFocus() {
        lifecycleScope.launch {
            val stopped = focusManager.stopFocus()
            if (stopped) {
                Toast.makeText(this@FocusActivity, R.string.focus_stopped, Toast.LENGTH_SHORT).show()
                refreshScreen()
            } else {
                Toast.makeText(this@FocusActivity, R.string.focus_stop_not_allowed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private class FocusCategoryAdapter(
    private val selectedIds: Set<String>,
    private val onCheckedChanged: (categoryId: String, checked: Boolean) -> Unit
) : ListAdapter<AppCategory, FocusCategoryAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppCategory>() {
        override fun areItemsTheSame(oldItem: AppCategory, newItem: AppCategory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: AppCategory, newItem: AppCategory) = oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemFocusCategoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: AppCategory) {
            binding.categoryCheckBox.setOnCheckedChangeListener(null)
            binding.categoryCheckBox.text = category.displayName
            binding.categoryCheckBox.isChecked = category.id in selectedIds
            binding.categoryCheckBox.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChanged(category.id, isChecked)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFocusCategoryBinding.inflate(
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
