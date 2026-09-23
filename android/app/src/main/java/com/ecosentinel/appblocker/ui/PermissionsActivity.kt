package com.ecosentinel.appblocker.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.admin.DeviceOwnerManager
import com.ecosentinel.appblocker.databinding.ActivityPermissionsBinding
import com.ecosentinel.appblocker.survival.AppHealthChecker
import com.ecosentinel.appblocker.survival.AppHealthSnapshot
import com.ecosentinel.appblocker.survival.SurvivalManager
import com.ecosentinel.appblocker.survival.SurvivalSettings
import com.ecosentinel.appblocker.util.PermissionCheckItem
import com.ecosentinel.appblocker.util.PermissionHelper
import com.ecosentinel.appblocker.util.PermissionKind

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private lateinit var adapter: PermissionCheckAdapter
    private lateinit var survivalAdapter: SurvivalStatusAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshList()
        SurvivalManager.runCheck(this, SurvivalManager.REASON_PERMISSIONS_SCREEN) {
            runOnUiThread { refreshList() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PermissionCheckAdapter { item -> openPermission(item) }
        binding.permissionsRecyclerView.prepareForScrollParent(this)
        binding.permissionsRecyclerView.adapter = adapter

        survivalAdapter = SurvivalStatusAdapter { item -> openSurvivalAction(item) }
        binding.survivalRecyclerView.prepareForScrollParent(this)
        binding.survivalRecyclerView.adapter = survivalAdapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSetupPermissions.setOnClickListener {
            val step = PermissionHelper.runNextSetupStep(this)
            if (step == PermissionKind.NOTIFICATIONS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.hasNotificationPermission(this)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnRunSurvivalCheck.setOnClickListener { runSurvivalCheck() }
        binding.btnApplyPolicies.setOnClickListener {
            DeviceOwnerManager.applyPolicies(this)
            Toast.makeText(this, R.string.policies_applied, Toast.LENGTH_SHORT).show()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        SurvivalManager.runCheck(this, SurvivalManager.REASON_PERMISSIONS_SCREEN) {
            runOnUiThread { refreshList() }
        }
    }

    private fun refreshList() {
        val snapshot = AppHealthChecker.check(this)
        val requiredIssues = SurvivalSettings.requiredIssues(this, snapshot)
        binding.survivalSummary.text = if (requiredIssues.isEmpty()) {
            getString(R.string.survival_mode_ready)
        } else {
            getString(R.string.survival_mode_needs_action)
        }
        binding.survivalSummary.setTextColor(
            androidx.core.content.ContextCompat.getColor(
                this,
                if (requiredIssues.isEmpty()) R.color.neon_primary else R.color.block_accent
            )
        )
        survivalAdapter.submitListRemeasure(
            binding.survivalRecyclerView,
            survivalStatusItems(snapshot)
        )
        adapter.submitListRemeasure(
            binding.permissionsRecyclerView,
            PermissionHelper.getPermissionCheckItems(this)
        )
    }

    private fun openPermission(item: PermissionCheckItem) {
        if (item.kind == PermissionKind.NOTIFICATIONS &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        PermissionHelper.openPermissionSettings(this, item.kind)
    }

    private fun runSurvivalCheck() {
        SurvivalManager.runCheck(this, SurvivalManager.REASON_MANUAL) {
            runOnUiThread { refreshList() }
        }
    }

    private fun openSurvivalAction(item: SurvivalStatusItem) {
        when (item.action ?: return) {
            SurvivalAction.RUN_CHECK -> runSurvivalCheck()
            SurvivalAction.USAGE_ACCESS -> PermissionHelper.openUsageAccessSettings(this)
            SurvivalAction.OVERLAY -> PermissionHelper.openOverlaySettings(this)
            SurvivalAction.ACCESSIBILITY -> PermissionHelper.openAccessibilitySettings(this)
            SurvivalAction.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !PermissionHelper.hasNotificationPermission(this)
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionHelper.openAppNotificationSettings(this)
                }
            }
            SurvivalAction.BATTERY_OPTIMIZATION -> PermissionHelper.requestIgnoreBatteryOptimizations(this)
        }
    }

    private fun survivalStatusItems(snapshot: AppHealthSnapshot): List<SurvivalStatusItem> {
        val accessibilityRequired = SurvivalSettings.isAccessibilityRequirementEnabled(this)
        return listOf(
            SurvivalStatusItem(
                id = "monitor",
                label = getString(R.string.status_service),
                statusText = getString(
                    if (snapshot.monitorRunning) {
                        R.string.survival_status_running
                    } else {
                        R.string.survival_status_stopped
                    }
                ),
                level = if (snapshot.monitorRunning) SurvivalStatusLevel.OK else SurvivalStatusLevel.WARNING,
                action = if (snapshot.monitorRunning) null else SurvivalAction.RUN_CHECK
            ),
            SurvivalStatusItem(
                id = "usage",
                label = getString(R.string.status_usage_access),
                statusText = getString(
                    if (snapshot.usageAccessGranted) {
                        R.string.permission_status_granted
                    } else {
                        R.string.permission_status_denied
                    }
                ),
                level = if (snapshot.usageAccessGranted) SurvivalStatusLevel.OK else SurvivalStatusLevel.WARNING,
                action = if (snapshot.usageAccessGranted) null else SurvivalAction.USAGE_ACCESS
            ),
            SurvivalStatusItem(
                id = "overlay",
                label = getString(R.string.status_overlay_access),
                statusText = getString(
                    if (snapshot.overlayGranted) {
                        R.string.permission_status_granted
                    } else {
                        R.string.permission_status_denied
                    }
                ),
                level = if (snapshot.overlayGranted) SurvivalStatusLevel.OK else SurvivalStatusLevel.WARNING,
                action = if (snapshot.overlayGranted) null else SurvivalAction.OVERLAY
            ),
            SurvivalStatusItem(
                id = "accessibility",
                label = getString(R.string.stayfree_accessibility_title),
                statusText = getString(
                    if (snapshot.accessibilityEnabled) {
                        R.string.survival_status_enabled
                    } else {
                        R.string.survival_status_disabled
                    }
                ),
                level = when {
                    snapshot.accessibilityEnabled -> SurvivalStatusLevel.OK
                    accessibilityRequired -> SurvivalStatusLevel.WARNING
                    else -> SurvivalStatusLevel.INFO
                },
                action = if (snapshot.accessibilityEnabled) null else SurvivalAction.ACCESSIBILITY
            ),
            SurvivalStatusItem(
                id = "notifications",
                label = getString(R.string.status_notifications),
                statusText = getString(
                    if (snapshot.notificationGranted) {
                        R.string.permission_status_granted
                    } else {
                        R.string.permission_status_denied
                    }
                ),
                level = if (snapshot.notificationGranted) SurvivalStatusLevel.OK else SurvivalStatusLevel.WARNING,
                action = if (snapshot.notificationGranted) null else SurvivalAction.NOTIFICATIONS
            ),
            SurvivalStatusItem(
                id = "battery_optimization",
                label = getString(R.string.status_battery),
                statusText = getString(
                    if (snapshot.batteryOptimizationIgnored) {
                        R.string.survival_status_ignored
                    } else {
                        R.string.survival_status_not_ignored
                    }
                ),
                level = if (snapshot.batteryOptimizationIgnored) {
                    SurvivalStatusLevel.OK
                } else {
                    SurvivalStatusLevel.WARNING
                },
                action = if (snapshot.batteryOptimizationIgnored) null else SurvivalAction.BATTERY_OPTIMIZATION
            ),
            SurvivalStatusItem(
                id = "device_owner",
                label = getString(R.string.status_device_owner),
                statusText = getString(
                    if (snapshot.deviceOwner) {
                        R.string.survival_status_active
                    } else {
                        R.string.survival_status_device_owner_optional
                    }
                ),
                level = if (snapshot.deviceOwner) SurvivalStatusLevel.OK else SurvivalStatusLevel.INFO,
                action = null
            )
        )
    }

    companion object {
        fun launch(from: android.content.Context) {
            from.startActivity(Intent(from, PermissionsActivity::class.java))
        }
    }
}

private class PermissionCheckAdapter(
    private val onOpenSettings: (PermissionCheckItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<PermissionCheckItem, PermissionCheckAdapter.ViewHolder>(
    Diff
) {

    object Diff : androidx.recyclerview.widget.DiffUtil.ItemCallback<PermissionCheckItem>() {
        override fun areItemsTheSame(oldItem: PermissionCheckItem, newItem: PermissionCheckItem) =
            oldItem.kind == newItem.kind

        override fun areContentsTheSame(oldItem: PermissionCheckItem, newItem: PermissionCheckItem) =
            oldItem == newItem
    }

    class ViewHolder(
        private val binding: com.ecosentinel.appblocker.databinding.ItemPermissionCheckBinding,
        private val onOpenSettings: (PermissionCheckItem) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PermissionCheckItem) {
            val context = binding.root.context
            binding.permissionLabel.text = item.label
            val (statusText, colorRes) = when (item.granted) {
                true -> context.getString(R.string.permission_status_granted) to R.color.neon_primary
                false -> context.getString(R.string.permission_status_denied) to R.color.block_accent
                null -> context.getString(R.string.permission_status_manual) to R.color.on_surface_variant
            }
            binding.permissionStatus.text = statusText
            binding.permissionStatus.setTextColor(
                androidx.core.content.ContextCompat.getColor(context, colorRes)
            )

            binding.btnOpenSettings.text = when {
                item.granted == true -> context.getString(R.string.permission_open_settings)
                item.granted == false -> context.getString(R.string.permission_grant_access)
                else -> context.getString(R.string.permission_open_settings)
            }
            binding.btnOpenSettings.setOnClickListener { onOpenSettings(item) }
        }
    }

    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = com.ecosentinel.appblocker.databinding.ItemPermissionCheckBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onOpenSettings)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private data class SurvivalStatusItem(
    val id: String,
    val label: String,
    val statusText: String,
    val level: SurvivalStatusLevel,
    val action: SurvivalAction?
)

private enum class SurvivalStatusLevel {
    OK,
    WARNING,
    INFO
}

private enum class SurvivalAction(val buttonTextRes: Int) {
    RUN_CHECK(R.string.survival_action_restore),
    USAGE_ACCESS(R.string.permission_grant_access),
    OVERLAY(R.string.permission_grant_access),
    ACCESSIBILITY(R.string.permission_grant_access),
    NOTIFICATIONS(R.string.permission_grant_access),
    BATTERY_OPTIMIZATION(R.string.permission_grant_access)
}

private class SurvivalStatusAdapter(
    private val onOpenSettings: (SurvivalStatusItem) -> Unit
) : androidx.recyclerview.widget.ListAdapter<SurvivalStatusItem, SurvivalStatusAdapter.ViewHolder>(
    Diff
) {

    object Diff : androidx.recyclerview.widget.DiffUtil.ItemCallback<SurvivalStatusItem>() {
        override fun areItemsTheSame(oldItem: SurvivalStatusItem, newItem: SurvivalStatusItem) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SurvivalStatusItem, newItem: SurvivalStatusItem) =
            oldItem == newItem
    }

    class ViewHolder(
        private val binding: com.ecosentinel.appblocker.databinding.ItemPermissionCheckBinding,
        private val onOpenSettings: (SurvivalStatusItem) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SurvivalStatusItem) {
            val context = binding.root.context
            binding.permissionLabel.text = item.label
            binding.permissionStatus.text = item.statusText
            val colorRes = when (item.level) {
                SurvivalStatusLevel.OK -> R.color.neon_primary
                SurvivalStatusLevel.WARNING -> R.color.block_accent
                SurvivalStatusLevel.INFO -> R.color.on_surface_variant
            }
            binding.permissionStatus.setTextColor(
                androidx.core.content.ContextCompat.getColor(context, colorRes)
            )

            val action = item.action
            if (action == null) {
                binding.btnOpenSettings.visibility = View.GONE
                binding.btnOpenSettings.setOnClickListener(null)
            } else {
                binding.btnOpenSettings.visibility = View.VISIBLE
                binding.btnOpenSettings.text = context.getString(action.buttonTextRes)
                binding.btnOpenSettings.setOnClickListener { onOpenSettings(item) }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: android.view.ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = com.ecosentinel.appblocker.databinding.ItemPermissionCheckBinding.inflate(
            android.view.LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onOpenSettings)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
