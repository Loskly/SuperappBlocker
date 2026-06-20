package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivityWeeklyReportBinding
import com.ecosentinel.appblocker.databinding.ItemStatsAppBinding
import com.ecosentinel.appblocker.tracker.AppCategoryHelper
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.tracker.WeeklyAppUsageDetail
import com.ecosentinel.appblocker.tracker.WeeklyUsageSummary
import com.ecosentinel.appblocker.ui.stats.ChartBarEntry
import com.ecosentinel.appblocker.ui.stats.ChartSliceEntry
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.ecosentinel.appblocker.util.PermissionHelper
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

class WeeklyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeeklyReportBinding
    private lateinit var usageTracker: UsageTracker
    private lateinit var appsAdapter: WeeklyAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeeklyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usageTracker = UsageTracker(this)
        appsAdapter = WeeklyAppAdapter(usageTracker) { app ->
            AppStatsDetailActivity.launch(
                this,
                app.packageName,
                app.label,
                usageTracker.todayKey()
            )
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnUsageAccess.setOnClickListener {
            PermissionHelper.openUsageAccessSettings(this)
        }
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsRecyclerView.adapter = appsAdapter

        loadReport()
    }

    override fun onResume() {
        super.onResume()
        if (::usageTracker.isInitialized) {
            loadReport()
        }
    }

    private fun loadReport() {
        val hasAccess = PermissionHelper.hasUsageAccess(this)
        binding.permissionHintText.visibility = if (hasAccess) View.GONE else View.VISIBLE
        binding.btnUsageAccess.visibility = if (hasAccess) View.GONE else View.VISIBLE
        binding.reportContent.visibility = if (hasAccess) View.VISIBLE else View.GONE
        if (!hasAccess) {
            return
        }

        val weekStart = intent.getStringExtra(EXTRA_WEEK_START)
            ?: usageTracker.calendarWeekStartDateKey()
        val weekEnd = usageTracker.effectiveWeekEndDateKey(
            weekStart,
            usageTracker.calendarWeekEndDateKey(weekStart)
        )

        binding.weekRangeText.text = usageTracker.formatWeekRange(weekStart, weekEnd)

        lifecycleScope.launch {
            val report = usageTracker.loadWeeklyReport(weekStart, weekEnd)
            renderReport(report)
        }
    }

    private fun renderReport(report: WeeklyUsageSummary) {
        binding.totalTimeText.text = usageTracker.formatDuration(report.totalMillis)
        binding.averageDailyText.text = getString(
            R.string.weekly_report_average_daily,
            usageTracker.formatDuration(report.averageDailyMillis)
        )

        binding.comparisonText.text = formatComparison(report)
        binding.busiestDayText.text = if (report.busiestDay == null) {
            getString(R.string.stats_peak_unknown)
        } else {
            getString(
                R.string.weekly_report_busiest_day,
                report.busiestDay.label,
                usageTracker.formatDuration(report.busiestDay.totalMillis)
            )
        }
        binding.lightestDayText.text = if (report.lightestDay == null) {
            getString(R.string.stats_peak_unknown)
        } else {
            getString(
                R.string.weekly_report_lightest_day,
                report.lightestDay.label,
                usageTracker.formatDuration(report.lightestDay.totalMillis)
            )
        }

        val topCategory = AppCategoryHelper.groupByCategory(this, report.byPackage).firstOrNull()
        binding.topCategoryText.text = if (topCategory == null) {
            getString(R.string.stats_peak_unknown)
        } else {
            getString(
                R.string.weekly_report_top_category,
                topCategory.categoryName,
                usageTracker.formatDuration(topCategory.millis)
            )
        }

        binding.activeAppsText.text = resources.getQuantityString(
            R.plurals.weekly_report_active_apps,
            report.activeAppCount,
            report.activeAppCount
        )

        val chartEntries = report.dailySummaries.map { summary ->
            ChartBarEntry(
                label = summary.label,
                minutes = TimeUnit.MILLISECONDS.toMinutes(summary.totalMillis)
            )
        }
        binding.weeklyChart.setData(chartEntries)

        val categories = AppCategoryHelper.groupByCategory(this, report.byPackage)
        val topCategories = categories.take(5)
        val otherMillis = categories.drop(5).sumOf { it.millis }
        val categoryEntries = topCategories.map { ChartSliceEntry(it.categoryName, it.millis) }.toMutableList()
        if (otherMillis > 0L) {
            categoryEntries += ChartSliceEntry(getString(R.string.category_other), otherMillis)
        }
        binding.categoryChart.setData(categoryEntries)

        val apps = usageTracker.toWeeklyAppDetails(report.byPackage, report.dayCount).take(20)
        appsAdapter.submitList(apps)
        binding.emptyAppsText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun formatComparison(report: WeeklyUsageSummary): String {
        val previous = report.previousWeekTotalMillis ?: return getString(R.string.weekly_report_no_previous)
        if (previous <= 0L) {
            return getString(R.string.weekly_report_no_previous)
        }
        val deltaMillis = report.totalMillis - previous
        val percent = abs(deltaMillis * 100f / previous).roundToInt()
        return when {
            deltaMillis > 0 -> getString(
                R.string.weekly_report_more_than_previous,
                percent,
                usageTracker.formatDuration(deltaMillis)
            )
            deltaMillis < 0 -> getString(
                R.string.weekly_report_less_than_previous,
                percent,
                usageTracker.formatDuration(-deltaMillis)
            )
            else -> getString(R.string.weekly_report_same_as_previous)
        }
    }

    companion object {
        private const val EXTRA_WEEK_START = "week_start"

        fun createIntent(context: Context, weekStartDateKey: String? = null): Intent {
            return Intent(context, WeeklyReportActivity::class.java).apply {
                weekStartDateKey?.let { putExtra(EXTRA_WEEK_START, it) }
            }
        }

        fun launch(context: Context) {
            context.startActivity(createIntent(context))
        }
    }
}

private class WeeklyAppAdapter(
    private val usageTracker: UsageTracker,
    private val onAppClick: (WeeklyAppUsageDetail) -> Unit
) : ListAdapter<WeeklyAppUsageDetail, WeeklyAppAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<WeeklyAppUsageDetail>() {
        override fun areItemsTheSame(oldItem: WeeklyAppUsageDetail, newItem: WeeklyAppUsageDetail) =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: WeeklyAppUsageDetail, newItem: WeeklyAppUsageDetail) =
            oldItem == newItem
    }

    inner class ViewHolder(
        private val binding: ItemStatsAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentItem: WeeklyAppUsageDetail? = null

        init {
            binding.root.setOnClickListener {
                currentItem?.let(onAppClick)
            }
        }

        fun bind(item: WeeklyAppUsageDetail) {
            currentItem = item
            binding.appNameText.text = item.label
            val percent = (item.shareOfTotal * 100).roundToInt()
            binding.usageText.text = binding.root.context.getString(
                R.string.weekly_report_app_usage,
                usageTracker.formatDuration(item.totalMillis),
                usageTracker.formatDuration(item.averageDailyMillis),
                percent
            )
            binding.usageProgress.progress = percent
            binding.appIcon.setImageDrawable(
                InstalledAppsHelper.getAppIcon(binding.root.context, item.packageName)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStatsAppBinding.inflate(
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
