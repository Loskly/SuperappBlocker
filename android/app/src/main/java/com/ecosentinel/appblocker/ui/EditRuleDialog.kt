package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.databinding.DialogEditRuleBinding
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockSchedule
import com.ecosentinel.appblocker.engine.CooldownSettings
import com.ecosentinel.appblocker.engine.RuleLockConfig
import com.ecosentinel.appblocker.engine.RuleLockMode
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.modules.applimit.AppLimitModule
import com.ecosentinel.appblocker.tracker.AppGroupHelper
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class EditRuleDialog : DialogFragment() {

    interface Listener {
        fun onRuleSaved()
    }

    private var _binding: DialogEditRuleBinding? = null
    private val binding get() = _binding!!

    private var ruleId: String = ""
    private var targetType = TargetType.APP
    private var packageName: String = ""
    private var categoryId: String = ""
    private var groupId: String = ""
    private var websiteDomain: String = ""
    private var targetLabel: String = ""
    private var editingExistingRule = false

    private var startHour = 22
    private var startMinute = 0
    private var endHour = 7
    private var endMinute = 0
    private var lockUntilHour = 21
    private var lockUntilMinute = 0

    private lateinit var groupAppsAdapter: GroupAppPickAdapter
    private val selectedGroupPackages = mutableSetOf<String>()
    private var allGroupApps: List<InstalledApp> = emptyList()
    private var groupAppsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_Appbllocker)
        ruleId = requireArguments().getString(ARG_RULE_ID).orEmpty()
        packageName = requireArguments().getString(ARG_PACKAGE_NAME).orEmpty()
        categoryId = requireArguments().getString(ARG_CATEGORY_ID).orEmpty()
        groupId = requireArguments().getString(ARG_GROUP_ID).orEmpty()
        websiteDomain = requireArguments().getString(ARG_WEBSITE_DOMAIN).orEmpty()
        targetLabel = requireArguments().getString(ARG_TARGET_LABEL).orEmpty()
        targetType = TargetType.valueOf(
            requireArguments().getString(ARG_TARGET_TYPE, TargetType.APP.name)
        )
        editingExistingRule = ruleId.isNotBlank()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogEditRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appNameText.text = targetLabel
        if (targetType == TargetType.CATEGORY) {
            binding.packageText.text = getString(R.string.rule_type_category)
        } else if (targetType == TargetType.CUSTOM_GROUP) {
            binding.packageText.text = getString(R.string.rule_type_group)
        } else if (targetType == TargetType.URL_PATTERN) {
            binding.packageText.text = getString(R.string.rule_type_website)
        } else {
            binding.packageText.text = packageName
        }

        if (targetType == TargetType.CUSTOM_GROUP && groupId.isNotBlank()) {
            setupGroupAppsSection()
        } else {
            binding.groupAppsSection.isVisible = false
        }

        binding.modeGroup.setOnCheckedChangeListener { _, _ ->
            updateModeVisibility()
            updateBlockActiveLockAvailability()
        }

        setupLockModeControls()

        binding.btnScheduleStart.setOnClickListener {
            showTimePicker(isStart = true)
        }
        binding.btnScheduleEnd.setOnClickListener {
            showTimePicker(isStart = false)
        }

        lifecycleScope.launch {
            val rule = if (editingExistingRule) {
                AppDatabase.getInstance(requireContext()).policyRuleDao().getById(ruleId)
            } else {
                null
            }
            if (rule != null) {
                if (websiteDomain.isBlank() && rule.targetType == TargetType.URL_PATTERN) {
                    websiteDomain = rule.featureId.orEmpty()
                }
                when (rule.blockMode) {
                    BlockMode.PERMANENT -> binding.radioPermanent.isChecked = true
                    BlockMode.TIME_LIMIT -> {
                        binding.radioTimeLimit.isChecked = true
                        binding.limitInput.setText((rule.dailyLimitMinutes ?: 60).toString())
                    }
                    BlockMode.TIME_OF_DAY -> {
                        binding.radioTimeOfDay.isChecked = true
                        BlockSchedule.fromJson(rule.scheduleJson)?.let { schedule ->
                            startHour = schedule.startHour
                            startMinute = schedule.startMinute
                            endHour = schedule.endHour
                            endMinute = schedule.endMinute
                        }
                    }
                    BlockMode.COOLDOWN -> {
                        binding.radioCooldown.isChecked = true
                        val settings = CooldownSettings.fromJson(rule.metadataJson) ?: CooldownSettings.DEFAULT
                        binding.cooldownUsageInput.setText(settings.usageMinutes.toString())
                        binding.cooldownWindowInput.setText(settings.windowMinutes.toString())
                        binding.cooldownBlockInput.setText(settings.blockMinutes.toString())
                    }
                }
                applyLockValues(rule)
                binding.modeGroup.isEnabled = false
                for (index in 0 until binding.modeGroup.childCount) {
                    binding.modeGroup.getChildAt(index).isEnabled = false
                }
            } else {
                if (targetType == TargetType.URL_PATTERN) {
                    binding.radioPermanent.isChecked = true
                } else {
                    binding.radioTimeLimit.isChecked = true
                    binding.limitInput.setText("60")
                }
                binding.cooldownUsageInput.setText(CooldownSettings.DEFAULT.usageMinutes.toString())
                binding.cooldownWindowInput.setText(CooldownSettings.DEFAULT.windowMinutes.toString())
                binding.cooldownBlockInput.setText(CooldownSettings.DEFAULT.blockMinutes.toString())
                binding.radioLockNormal.isChecked = true
                binding.radioLockDelay15.isChecked = true
            }
            updateSchedulePreview()
            updateModeVisibility()
            applyWebsiteModeRestrictions()
            updateStrictOptionsVisibility()
            updateCustomLockPreview()
            updateBlockActiveLockAvailability()
        }

        binding.btnSave.setOnClickListener {
            lifecycleScope.launch { saveRule() }
        }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun setupGroupAppsSection() {
        binding.groupAppsSection.isVisible = true
        groupAppsExpanded = false
        groupAppsAdapter = GroupAppPickAdapter(selectedGroupPackages) { packageName, checked ->
            if (checked) {
                selectedGroupPackages.add(packageName)
            } else {
                selectedGroupPackages.remove(packageName)
            }
            applyGroupAppsFilter(binding.groupAppsSearchInput.text?.toString().orEmpty())
        }
        binding.groupAppsRecyclerView.prepareForScrollParent(requireContext())
        binding.groupAppsRecyclerView.adapter = groupAppsAdapter
        binding.btnToggleGroupAppsList.setOnClickListener {
            groupAppsExpanded = !groupAppsExpanded
            applyGroupAppsFilter(binding.groupAppsSearchInput.text?.toString().orEmpty())
        }

        binding.groupAppsSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyGroupAppsFilter(s?.toString().orEmpty())
            }
        })

        binding.groupAppsLoadingText.isVisible = true
        lifecycleScope.launch {
            val members = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).appGroupDao().getMemberPackages(groupId)
            }
            selectedGroupPackages.clear()
            selectedGroupPackages.addAll(members)

            allGroupApps = withContext(Dispatchers.IO) {
                InstalledAppsHelper.getAllInstalledApps(requireContext())
            }
            binding.groupAppsLoadingText.isVisible = false
            groupAppsAdapter.notifyDataSetChanged()
            applyGroupAppsFilter(binding.groupAppsSearchInput.text?.toString().orEmpty())
        }
    }

    private fun applyGroupAppsFilter(queryRaw: String) {
        if (!::groupAppsAdapter.isInitialized) {
            return
        }
        val query = queryRaw.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allGroupApps
        } else {
            allGroupApps.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }
        val sorted = filtered.sortedForGroupPicker()
        val visibleApps = if (groupAppsExpanded) {
            sorted
        } else {
            sorted.take(COLLAPSED_GROUP_APPS_COUNT)
        }
        groupAppsAdapter.submitListRemeasure(binding.groupAppsRecyclerView, visibleApps)

        binding.btnToggleGroupAppsList.isVisible = sorted.size > COLLAPSED_GROUP_APPS_COUNT
        binding.btnToggleGroupAppsList.text = if (groupAppsExpanded) {
            getString(R.string.group_apps_collapse)
        } else {
            getString(R.string.group_apps_expand, sorted.size)
        }

        val hasApps = allGroupApps.isNotEmpty()
        binding.groupAppsEmptyText.isVisible = hasApps && sorted.isEmpty()
    }

    private fun List<InstalledApp>.sortedForGroupPicker(): List<InstalledApp> {
        return sortedWith(
            compareBy<InstalledApp> { it.packageName !in selectedGroupPackages }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName.lowercase() }
        )
    }

    private fun applyWebsiteModeRestrictions() {
        if (targetType != TargetType.URL_PATTERN) {
            return
        }
        binding.radioTimeLimit.visibility = View.GONE
        binding.radioCooldown.visibility = View.GONE
        if (binding.radioTimeLimit.isChecked || binding.radioCooldown.isChecked) {
            binding.radioPermanent.isChecked = true
        }
    }

    private fun setupLockModeControls() {
        binding.lockModeGroup.setOnCheckedChangeListener { _, _ ->
            updateStrictOptionsVisibility()
        }
        binding.checkLockUntilCustomTime.setOnCheckedChangeListener { _, _ ->
            updateStrictOptionsVisibility()
            updateCustomLockPreview()
        }
        binding.checkLockDelay.setOnCheckedChangeListener { _, checked ->
            binding.lockDelayGroup.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked && binding.lockDelayGroup.checkedRadioButtonId == View.NO_ID) {
                binding.radioLockDelay15.isChecked = true
            }
        }
        binding.btnLockUntilCustomTime.setOnClickListener {
            showLockUntilTimePicker()
        }
    }

    private fun updateModeVisibility() {
        val isTimeLimit = binding.radioTimeLimit.isChecked
        val isTimeOfDay = binding.radioTimeOfDay.isChecked
        val isCooldown = binding.radioCooldown.isChecked
        binding.limitInputLayout.visibility = if (isTimeLimit) View.VISIBLE else View.GONE
        binding.scheduleInputLayout.visibility = if (isTimeOfDay) View.VISIBLE else View.GONE
        binding.cooldownInputLayout.visibility = if (isCooldown) View.VISIBLE else View.GONE
    }

    private fun updateStrictOptionsVisibility() {
        val isStrict = binding.radioLockStrict.isChecked
        binding.strictOptionsLayout.visibility = if (isStrict) View.VISIBLE else View.GONE
        binding.customLockTimeLayout.visibility =
            if (isStrict && binding.checkLockUntilCustomTime.isChecked) View.VISIBLE else View.GONE
        binding.lockDelayGroup.visibility =
            if (isStrict && binding.checkLockDelay.isChecked) View.VISIBLE else View.GONE
    }

    private fun updateBlockActiveLockAvailability() {
        val supportsActiveBlockLock = binding.radioTimeLimit.isChecked ||
            binding.radioTimeOfDay.isChecked ||
            binding.radioCooldown.isChecked
        binding.checkLockOnBlockActive.isEnabled = supportsActiveBlockLock
        if (!supportsActiveBlockLock) {
            binding.checkLockOnBlockActive.isChecked = false
        }
    }

    private fun updateSchedulePreview() {
        val schedule = BlockSchedule(startHour, startMinute, endHour, endMinute)
        binding.schedulePreviewText.text = getString(
            R.string.schedule_preview,
            schedule.formatRange()
        )
        binding.btnScheduleStart.text = getString(
            R.string.schedule_start_value,
            BlockSchedule.formatTime(startHour, startMinute)
        )
        binding.btnScheduleEnd.text = getString(
            R.string.schedule_end_value,
            BlockSchedule.formatTime(endHour, endMinute)
        )
    }

    private fun updateCustomLockPreview() {
        binding.lockUntilCustomPreview.text = getString(
            R.string.rule_lock_until_time_preview,
            BlockSchedule.formatTime(lockUntilHour, lockUntilMinute)
        )
        binding.btnLockUntilCustomTime.text = getString(
            R.string.rule_lock_until_time_preview,
            BlockSchedule.formatTime(lockUntilHour, lockUntilMinute)
        )
    }

    private fun applyLockValues(rule: com.ecosentinel.appblocker.data.entity.PolicyRuleEntity) {
        val nowMillis = System.currentTimeMillis()
        val customUntil = rule.lockUntilCustomMillis?.takeIf { it > nowMillis }

        if (rule.lockMode == RuleLockMode.STRICT) {
            binding.radioLockStrict.isChecked = true
            binding.checkLockUntilEndOfDay.isChecked =
                rule.lockUntilDayEndMillis?.let { it > nowMillis } == true
            binding.checkLockUntilCustomTime.isChecked = customUntil != null
            customUntil?.let { millis ->
                Calendar.getInstance().apply {
                    timeInMillis = millis
                    lockUntilHour = get(Calendar.HOUR_OF_DAY)
                    lockUntilMinute = get(Calendar.MINUTE)
                }
            }
            binding.checkLockOnBlockActive.isChecked = rule.lockOnBlockActive
            val delayMinutes = rule.lockDelayMinutes
            binding.checkLockDelay.isChecked = delayMinutes != null
            when (delayMinutes) {
                30 -> binding.radioLockDelay30.isChecked = true
                60 -> binding.radioLockDelay60.isChecked = true
                else -> binding.radioLockDelay15.isChecked = true
            }
        } else {
            binding.radioLockNormal.isChecked = true
            binding.radioLockDelay15.isChecked = true
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(if (isStart) startHour else endHour)
            .setMinute(if (isStart) startMinute else endMinute)
            .setTitleText(
                if (isStart) {
                    getString(R.string.schedule_start)
                } else {
                    getString(R.string.schedule_end)
                }
            )
            .build()

        picker.addOnPositiveButtonClickListener {
            if (isStart) {
                startHour = picker.hour
                startMinute = picker.minute
            } else {
                endHour = picker.hour
                endMinute = picker.minute
            }
            updateSchedulePreview()
        }
        picker.show(parentFragmentManager, if (isStart) "start_time" else "end_time")
    }

    private fun showLockUntilTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(lockUntilHour)
            .setMinute(lockUntilMinute)
            .setTitleText(getString(R.string.rule_lock_pick_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            lockUntilHour = picker.hour
            lockUntilMinute = picker.minute
            updateCustomLockPreview()
        }
        picker.show(parentFragmentManager, "lock_until_time")
    }

    private suspend fun saveRule() {
        val blockMode = when {
            binding.radioPermanent.isChecked -> BlockMode.PERMANENT
            binding.radioTimeOfDay.isChecked -> BlockMode.TIME_OF_DAY
            binding.radioCooldown.isChecked -> BlockMode.COOLDOWN
            else -> BlockMode.TIME_LIMIT
        }

        val limitMinutes = if (blockMode == BlockMode.TIME_LIMIT) {
            binding.limitInput.text?.toString()?.toIntOrNull()?.takeIf { it > 0 }
        } else {
            null
        }

        val schedule = if (blockMode == BlockMode.TIME_OF_DAY) {
            BlockSchedule(startHour, startMinute, endHour, endMinute)
        } else {
            null
        }

        val cooldownSettings = if (blockMode == BlockMode.COOLDOWN) {
            CooldownSettings(
                usageMinutes = binding.cooldownUsageInput.text?.toString()?.toIntOrNull() ?: 0,
                windowMinutes = binding.cooldownWindowInput.text?.toString()?.toIntOrNull() ?: 0,
                blockMinutes = binding.cooldownBlockInput.text?.toString()?.toIntOrNull() ?: 0
            )
        } else {
            null
        }

        if (blockMode == BlockMode.TIME_LIMIT && targetType == TargetType.URL_PATTERN) {
            Toast.makeText(requireContext(), getString(R.string.website_mode_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        if (blockMode == BlockMode.COOLDOWN && targetType == TargetType.URL_PATTERN) {
            Toast.makeText(requireContext(), getString(R.string.website_mode_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        if (blockMode == BlockMode.TIME_LIMIT && limitMinutes == null) {
            Toast.makeText(requireContext(), getString(R.string.limit_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (blockMode == BlockMode.TIME_OF_DAY && (schedule == null || !schedule.isValid())) {
            Toast.makeText(requireContext(), getString(R.string.schedule_required), Toast.LENGTH_SHORT).show()
            return
        }

        if (blockMode == BlockMode.COOLDOWN && (cooldownSettings == null || !cooldownSettings.isValid())) {
            Toast.makeText(requireContext(), getString(R.string.cooldown_required), Toast.LENGTH_SHORT).show()
            return
        }

        val lockConfig = buildRuleLockConfig() ?: return

        if (targetType == TargetType.CUSTOM_GROUP && groupId.isNotBlank()) {
            if (selectedGroupPackages.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.group_apps_required), Toast.LENGTH_SHORT).show()
                return
            }
            AppGroupHelper.saveGroupMembers(requireContext(), groupId, selectedGroupPackages.toSet())
        }

        val dao = AppDatabase.getInstance(requireContext()).policyRuleDao()
        val existingRule = if (editingExistingRule) {
            dao.getById(ruleId)
        } else {
            when (targetType) {
                TargetType.CATEGORY -> dao.getByCategoryIdAndMode(categoryId, blockMode)
                TargetType.CUSTOM_GROUP -> dao.getByGroupIdAndMode(groupId, blockMode)
                TargetType.URL_PATTERN -> dao.getByWebsiteDomainAndMode(websiteDomain, blockMode)
                else -> dao.getByPackageNameAndMode(packageName, blockMode)
            }
        }

        val rule = when (targetType) {
            TargetType.CATEGORY -> AppLimitModule.createCategoryRule(
                categoryId = categoryId,
                blockMode = blockMode,
                dailyLimitMinutes = limitMinutes,
                schedule = schedule,
                cooldownSettings = cooldownSettings,
                enabled = existingRule?.enabled ?: true,
                lockConfig = lockConfig
            )
            TargetType.CUSTOM_GROUP -> AppLimitModule.createGroupRule(
                groupId = groupId,
                blockMode = blockMode,
                dailyLimitMinutes = limitMinutes,
                schedule = schedule,
                cooldownSettings = cooldownSettings,
                enabled = existingRule?.enabled ?: true,
                lockConfig = lockConfig
            )
            TargetType.URL_PATTERN -> AppLimitModule.createWebsiteRule(
                domain = websiteDomain,
                blockMode = blockMode,
                schedule = schedule,
                enabled = existingRule?.enabled ?: true,
                lockConfig = lockConfig
            )
            else -> AppLimitModule.createRule(
                packageName = packageName,
                blockMode = blockMode,
                dailyLimitMinutes = limitMinutes,
                schedule = schedule,
                cooldownSettings = cooldownSettings,
                enabled = existingRule?.enabled ?: true,
                lockConfig = lockConfig
            )
        }
        dao.upsert(rule)
        (activity as? Listener)?.onRuleSaved()
        dismiss()
    }

    private fun buildRuleLockConfig(): RuleLockConfig? {
        if (!binding.radioLockStrict.isChecked) {
            return RuleLockConfig.NORMAL
        }

        val untilDayEndMillis = if (binding.checkLockUntilEndOfDay.isChecked) {
            endOfTodayMillis()
        } else {
            null
        }
        val untilCustomMillis = if (binding.checkLockUntilCustomTime.isChecked) {
            nextCustomLockMillis()
        } else {
            null
        }
        val delayMinutes = if (binding.checkLockDelay.isChecked) {
            when {
                binding.radioLockDelay60.isChecked -> 60
                binding.radioLockDelay30.isChecked -> 30
                else -> 15
            }
        } else {
            null
        }

        val hasCondition = untilDayEndMillis != null ||
            untilCustomMillis != null ||
            binding.checkLockOnBlockActive.isChecked ||
            delayMinutes != null

        if (!hasCondition) {
            Toast.makeText(requireContext(), R.string.rule_lock_condition_required, Toast.LENGTH_SHORT).show()
            return null
        }

        return RuleLockConfig(
            mode = RuleLockMode.STRICT,
            untilDayEndMillis = untilDayEndMillis,
            untilCustomMillis = untilCustomMillis,
            onBlockActive = binding.checkLockOnBlockActive.isChecked,
            delayMinutes = delayMinutes,
            delayStartedAtMillis = null
        )
    }

    private fun endOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    private fun nextCustomLockMillis(): Long {
        val nowMillis = System.currentTimeMillis()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, lockUntilHour)
            set(Calendar.MINUTE, lockUntilMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val COLLAPSED_GROUP_APPS_COUNT = 5

        private const val ARG_RULE_ID = "rule_id"
        private const val ARG_PACKAGE_NAME = "package_name"
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_WEBSITE_DOMAIN = "website_domain"
        private const val ARG_TARGET_LABEL = "target_label"
        private const val ARG_TARGET_TYPE = "target_type"

        fun newInstance(packageName: String, appLabel: String): EditRuleDialog {
            return EditRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_PACKAGE_NAME, packageName)
                    putString(ARG_TARGET_LABEL, appLabel)
                    putString(ARG_TARGET_TYPE, TargetType.APP.name)
                }
            }
        }

        fun newInstanceForCategory(categoryId: String, categoryLabel: String): EditRuleDialog {
            return EditRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY_ID, categoryId)
                    putString(ARG_TARGET_LABEL, categoryLabel)
                    putString(ARG_TARGET_TYPE, TargetType.CATEGORY.name)
                }
            }
        }

        fun newInstanceForGroup(groupId: String, groupLabel: String): EditRuleDialog {
            return EditRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                    putString(ARG_TARGET_LABEL, groupLabel)
                    putString(ARG_TARGET_TYPE, TargetType.CUSTOM_GROUP.name)
                }
            }
        }

        fun newInstanceForWebsite(domain: String): EditRuleDialog {
            return EditRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_WEBSITE_DOMAIN, domain)
                    putString(ARG_TARGET_LABEL, domain)
                    putString(ARG_TARGET_TYPE, TargetType.URL_PATTERN.name)
                }
            }
        }

        fun newInstanceForEdit(
            ruleId: String,
            targetLabel: String,
            targetType: TargetType,
            packageName: String = "",
            categoryId: String = "",
            groupId: String = "",
            websiteDomain: String = ""
        ): EditRuleDialog {
            return EditRuleDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_RULE_ID, ruleId)
                    putString(ARG_PACKAGE_NAME, packageName)
                    putString(ARG_CATEGORY_ID, categoryId)
                    putString(ARG_GROUP_ID, groupId)
                    putString(ARG_WEBSITE_DOMAIN, websiteDomain)
                    putString(ARG_TARGET_LABEL, targetLabel)
                    putString(ARG_TARGET_TYPE, targetType.name)
                }
            }
        }
    }
}
