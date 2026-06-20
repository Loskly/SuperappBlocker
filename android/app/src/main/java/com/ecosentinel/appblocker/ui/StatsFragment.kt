package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.databinding.FragmentStatsBinding
import com.ecosentinel.appblocker.databinding.ItemStatsAppBinding
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.AppUsageDetail
import com.ecosentinel.appblocker.tracker.DailyUsageSummary
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.ui.stats.ChartBarEntry
import com.ecosentinel.appblocker.ui.stats.ChartSliceEntry
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.ecosentinel.appblocker.util.PermissionHelper
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var usageTracker: UsageTracker
    private lateinit var statsAdapter: StatsAppAdapter

    private var weeklySummaries: List<DailyUsageSummary> = emptyList()
    private var selectedDayUsage: Map<String, Long> = emptyMap()
    private var selectedDateKey: String = ""
    private var ruledPackages: Set<String> = emptySet()
    private var ruledCategoryIds: Set<String> = emptySet()

    private enum class StatsFilter { ALL, TOP_10, WITH_RULES }

    private var currentFilter = StatsFilter.ALL
    private var showSystemApps = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        usageTracker = UsageTracker(requireContext())
        selectedDateKey = savedInstanceState?.getString(STATE_SELECTED_DATE_KEY)
            ?: usageTracker.todayKey()

        statsAdapter = StatsAppAdapter { app ->
            AppStatsDetailActivity.launch(
                requireContext(),
                app.packageName,
                app.label,
                selectedDateKey
            )
        }
        binding.statsAppsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.statsAppsRecyclerView.adapter = statsAdapter

        setupFilters()
        setupDatePicker()
        binding.btnWeeklyReport.setOnClickListener {
            WeeklyReportActivity.launch(requireContext())
        }
        binding.btnStatsUsageAccess.setOnClickListener {
            PermissionHelper.openUsageAccessSettings(requireContext())
        }
        updateDateButtonLabel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_DATE_KEY, selectedDateKey)
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupDatePicker() {
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val minUtcMillis = usageTracker.dateKeyToPickerUtcMillis(usageTracker.minSelectableDateKey())
        val maxUtcMillis = usageTracker.dateKeyToPickerUtcMillis(usageTracker.todayKey())
        val constraints = CalendarConstraints.Builder()
            .setStart(minUtcMillis)
            .setEnd(maxUtcMillis)
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.stats_date_picker_title)
            .setSelection(usageTracker.dateKeyToPickerUtcMillis(selectedDateKey))
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            selectedDateKey = usageTracker.pickerUtcMillisToDateKey(selection)
            updateDateButtonLabel()
            refreshStats()
        }
        picker.show(parentFragmentManager, DATE_PICKER_TAG)
    }

    private fun updateDateButtonLabel() {
        binding.btnSelectDate.text = usageTracker.formatDisplayDate(selectedDateKey)
    }

    private fun updateSectionLabels() {
        val sectionDate = usageTracker.formatSectionDate(selectedDateKey)
        binding.totalTimeLabel.text = if (usageTracker.isToday(selectedDateKey)) {
            getString(R.string.stats_total_today)
        } else {
            getString(R.string.stats_total_day, sectionDate)
        }
        binding.categoriesChartLabel.text = if (usageTracker.isToday(selectedDateKey)) {
            getString(R.string.stats_categories_chart)
        } else {
            getString(R.string.stats_categories_day, sectionDate)
        }
        binding.appsSectionLabel.text = if (usageTracker.isToday(selectedDateKey)) {
            getString(R.string.stats_apps_section)
        } else {
            getString(R.string.stats_apps_day, sectionDate)
        }
    }

    private fun setupFilters() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipTop10 -> StatsFilter.TOP_10
                R.id.chipWithRules -> StatsFilter.WITH_RULES
                else -> StatsFilter.ALL
            }
            renderAppList()
        }
        binding.showSystemSwitch.setOnCheckedChangeListener { _, isChecked ->
            showSystemApps = isChecked
            renderCategoryChart()
            renderAppList()
        }
    }

    private fun refreshStats() {
        val hasAccess = PermissionHelper.hasUsageAccess(requireContext())
        binding.statsPermissionHint.visibility = if (hasAccess) View.GONE else View.VISIBLE
        binding.btnStatsUsageAccess.visibility = if (hasAccess) View.GONE else View.VISIBLE
        binding.totalTimeCard.visibility = if (hasAccess) View.VISIBLE else View.GONE
        binding.btnSelectDate.visibility = if (hasAccess) View.VISIBLE else View.GONE

        if (!hasAccess) {
            statsAdapter.submitList(emptyList())
            binding.emptyStatsText.visibility = View.VISIBLE
            binding.weeklyChart.setData(emptyList())
            binding.categoryChart.setData(emptyList())
            return
        }

        updateSectionLabels()

        viewLifecycleOwner.lifecycleScope.launch {
            ruledPackages = AppDatabase.getInstance(requireContext())
                .policyRuleDao()
                .getAllRules()
                .filter { it.enabled && it.targetType == TargetType.APP }
                .mapNotNull { it.packageName }
                .toSet()
            ruledCategoryIds = AppDatabase.getInstance(requireContext())
                .policyRuleDao()
                .getAllRules()
                .filter { it.enabled && it.targetType == TargetType.CATEGORY }
                .mapNotNull { it.featureId }
                .toSet()

            weeklySummaries = usageTracker.loadDailySummaries(7)
            val selectedSummary = usageTracker.loadDaySummary(selectedDateKey)
            selectedDayUsage = selectedSummary.byPackage

            renderSummaryCards(selectedSummary.totalMillis)
            renderWeeklyChart()
            renderCategoryChart()
            renderPeakHourInline()
            renderAppList()
        }
    }

    private fun renderSummaryCards(selectedTotalMillis: Long) {
        binding.totalTimeText.text = usageTracker.formatDuration(selectedTotalMillis)

        val average = if (weeklySummaries.isNotEmpty()) {
            weeklySummaries.map { it.totalMillis }.average().toLong()
        } else {
            0L
        }
        binding.averageTimeText.text = getString(
            R.string.stats_average_week,
            usageTracker.formatDuration(average)
        )
    }

    private suspend fun renderPeakHourInline() {
        val peak = usageTracker.getPeakPeriodForDate(selectedDateKey)
        binding.peakHourText.text = if (peak == null) {
            getString(R.string.stats_peak_unknown)
        } else {
            getString(
                R.string.stats_peak_value,
                usageTracker.formatHourRange(peak.startHour, peak.endHour),
                usageTracker.formatDuration(peak.millis)
            )
        }
    }

    private fun renderWeeklyChart() {
        val entries = weeklySummaries.map { summary ->
            ChartBarEntry(
                label = summary.label,
                minutes = TimeUnit.MILLISECONDS.toMinutes(summary.totalMillis)
            )
        }
        binding.weeklyChart.setData(entries)
    }

    private fun renderCategoryChart() {
        val categories = AppCategoryHelper.groupByCategory(requireContext(), selectedDayUsage)
        val topCategories = categories.take(5)
        val otherMillis = categories.drop(5).sumOf { it.millis }
        val chartEntries = topCategories.map { ChartSliceEntry(it.categoryName, it.millis) }.toMutableList()
        if (otherMillis > 0L) {
            chartEntries += ChartSliceEntry(getString(R.string.category_other), otherMillis)
        }
        binding.categoryChart.setData(chartEntries)
    }

    private fun renderAppList() {
        val context = requireContext()
        var apps = AppCategoryHelper.toAppDetails(context, selectedDayUsage, showSystemApps)
        apps = when (currentFilter) {
            StatsFilter.TOP_10 -> apps.take(10)
            StatsFilter.WITH_RULES -> apps.filter { app ->
                ruledPackages.contains(app.packageName) ||
                    ruledCategoryIds.any { categoryId ->
                        AppCategoryHelper.belongsToCategory(context, app.packageName, categoryId)
                    }
            }
            StatsFilter.ALL -> apps
        }
        statsAdapter.submitList(apps)
        binding.emptyStatsText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
    }

    companion object {
        private const val STATE_SELECTED_DATE_KEY = "selected_date_key"
        private const val DATE_PICKER_TAG = "stats_date_picker"
    }
}

class StatsAppAdapter(
    private val onAppClick: (AppUsageDetail) -> Unit
) : ListAdapter<AppUsageDetail, StatsAppAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppUsageDetail>() {
        override fun areItemsTheSame(oldItem: AppUsageDetail, newItem: AppUsageDetail) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppUsageDetail, newItem: AppUsageDetail) =
            oldItem == newItem
    }

    class ViewHolder(
        private val binding: ItemStatsAppBinding,
        private val onAppClick: (AppUsageDetail) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentItem: AppUsageDetail? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let(onAppClick)
            }
        }

        fun bind(item: AppUsageDetail) {
            currentItem = item
            binding.appNameText.text = item.label
            val percent = (item.shareOfTotal * 100).roundToInt()
            binding.usageText.text = "${formatDuration(item.usedMillis)} · $percent%"
            binding.usageProgress.progress = percent
            binding.appIcon.setImageDrawable(
                InstalledAppsHelper.getAppIcon(binding.root.context, item.packageName)
            )
        }

        private fun formatDuration(millis: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            val hours = minutes / 60
            val mins = minutes % 60
            return if (hours > 0) "${hours}ч ${mins}мин" else "${mins}мин"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatsAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onAppClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
