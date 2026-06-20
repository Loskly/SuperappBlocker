package com.ecosentinel.appblocker.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.admin.DeviceOwnerManager
import com.ecosentinel.appblocker.databinding.ActivityPermissionsBinding
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.util.PermissionCheckItem
import com.ecosentinel.appblocker.util.PermissionHelper
import com.ecosentinel.appblocker.util.PermissionKind

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsBinding
    private lateinit var adapter: PermissionCheckAdapter

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshList()
        MonitorBootstrap.ensureMonitoring(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PermissionCheckAdapter { item -> openPermission(item) }
        binding.permissionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.permissionsRecyclerView.adapter = adapter
        binding.permissionsRecyclerView.setHasFixedSize(false)

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
        binding.btnApplyPolicies.setOnClickListener {
            DeviceOwnerManager.applyPolicies(this)
            Toast.makeText(this, R.string.policies_applied, Toast.LENGTH_SHORT).show()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        MonitorBootstrap.ensureMonitoring(this)
    }

    private fun refreshList() {
        adapter.submitList(PermissionHelper.getPermissionCheckItems(this))
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
