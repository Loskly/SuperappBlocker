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
import com.ecosentinel.appblocker.databinding.ActivityAppStatsDetailBinding
import com.ecosentinel.appblocker.databinding.ItemAppSessionBinding
import com.ecosentinel.appblocker.tracker.AppUsageSession
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.ecosentinel.appblocker.util.PermissionHelper
import kotlinx.coroutines.launch

class AppStatsDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppStatsDetailBinding
    private lateinit var usageTracker: UsageTracker
    private lateinit var sessionAdapter: AppSessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppStatsDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL)
            ?: InstalledAppsHelper.getAppLabel(this, packageName)
        val dateKey = intent.getStringExtra(EXTRA_DATE_KEY) ?: UsageTracker(this).todayKey()

        usageTracker = UsageTracker(this)
        sessionAdapter = AppSessionAdapter(usageTracker)

        binding.btnBack.setOnClickListener { finish() }
        binding.appNameText.text = appLabel
        binding.screenTitle.text = appLabel
        binding.appIcon.setImageDrawable(InstalledAppsHelper.getAppIcon(this, packageName))

        val displayDate = usageTracker.formatDisplayDate(dateKey)
        binding.dateText.text = if (usageTracker.isToday(dateKey)) {
            getString(R.string.app_stats_today)
        } else {
            getString(R.string.app_stats_day, displayDate)
        }
        binding.sessionsSectionLabel.text = if (usageTracker.isToday(dateKey)) {
            getString(R.string.app_stats_sessions_list)
        } else {
            getString(R.string.app_stats_sessions_list_day, displayDate.lowercase())
        }

        binding.sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sessionsRecyclerView.adapter = sessionAdapter
        binding.sessionsRecyclerView.setHasFixedSize(false)

        if (!PermissionHelper.hasUsageAccess(this)) {
            binding.totalTimeText.text = getString(R.string.stats_peak_unknown)
            binding.sessionCountText.text = "0"
            binding.avgSessionText.text = "—"
            binding.emptySessionsText.visibility = View.VISIBLE
            return
        }

        lifecycleScope.launch {
            val detail = usageTracker.getAppDayDetail(packageName, dateKey)
            binding.totalTimeText.text = usageTracker.formatDuration(detail.totalMillis)
            binding.sessionCountText.text = detail.sessionCount.toString()
            binding.avgSessionText.text = if (detail.sessionCount > 0) {
                usageTracker.formatDuration(detail.averageSessionMillis)
            } else {
                "—"
            }
            binding.hourlyChart.setData(detail.hourlyBuckets)
            sessionAdapter.submitList(detail.sessions)
            binding.emptySessionsText.text = if (usageTracker.isToday(dateKey)) {
                getString(R.string.app_stats_no_sessions)
            } else {
                getString(R.string.app_stats_no_sessions_day)
            }
            binding.emptySessionsText.visibility =
                if (detail.sessions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_APP_LABEL = "extra_app_label"
        private const val EXTRA_DATE_KEY = "extra_date_key"

        fun launch(
            context: Context,
            packageName: String,
            appLabel: String,
            dateKey: String = UsageTracker(context).todayKey()
        ) {
            context.startActivity(
                Intent(context, AppStatsDetailActivity::class.java).apply {
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                    putExtra(EXTRA_APP_LABEL, appLabel)
                    putExtra(EXTRA_DATE_KEY, dateKey)
                }
            )
        }
    }
}

private class AppSessionAdapter(
    private val usageTracker: UsageTracker
) : ListAdapter<AppUsageSession, AppSessionAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<AppUsageSession>() {
        override fun areItemsTheSame(oldItem: AppUsageSession, newItem: AppUsageSession) =
            oldItem.startMillis == newItem.startMillis && oldItem.endMillis == newItem.endMillis

        override fun areContentsTheSame(oldItem: AppUsageSession, newItem: AppUsageSession) =
            oldItem == newItem
    }

    class ViewHolder(
        private val binding: ItemAppSessionBinding,
        private val usageTracker: UsageTracker
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(session: AppUsageSession) {
            binding.sessionTimeText.text = usageTracker.formatTimeRange(
                session.startMillis,
                session.endMillis
            )
            binding.sessionDurationText.text = usageTracker.formatDuration(session.durationMillis)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppSessionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, usageTracker)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
