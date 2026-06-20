package com.ecosentinel.appblocker.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.admin.DeviceOwnerManager
import com.ecosentinel.appblocker.databinding.FragmentHomeBinding
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.service.UsageMonitorService
import com.ecosentinel.appblocker.sync.DeviceTokenStore
import com.ecosentinel.appblocker.sync.SyncApi
import com.ecosentinel.appblocker.util.PermissionHelper
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var tokenStore: DeviceTokenStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tokenStore = DeviceTokenStore(requireContext())

        binding.btnCheckPermissions.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionsActivity::class.java))
        }
        binding.btnSync.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = SyncApi(requireContext()).sync()
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorBootstrap.ensureMonitoring(requireContext())
        refreshStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun refreshStatus() {
        val context = requireContext()
        binding.statusDeviceOwner.text = getString(
            R.string.status_line,
            getString(R.string.status_device_owner),
            if (DeviceOwnerManager.isDeviceOwner(context)) getString(R.string.ok_status) else getString(R.string.no_status)
        )
        val (granted, total) = PermissionHelper.countGrantedPermissions(context)
        binding.statusPermissionsSummary.text = getString(
            R.string.permissions_summary,
            granted,
            total
        )
        binding.statusService.text = when {
            !PermissionHelper.hasUsageAccess(context) -> getString(
                R.string.status_line,
                getString(R.string.status_service),
                getString(R.string.monitor_waiting_permissions)
            )
            UsageMonitorService.isRunning(context) -> getString(
                R.string.status_line,
                getString(R.string.status_service),
                getString(R.string.monitor_always_on)
            )
            else -> getString(
                R.string.status_line,
                getString(R.string.status_service),
                getString(R.string.monitor_starting)
            )
        }
        binding.pairingCodeText.text = getString(R.string.pairing_code_value, tokenStore.getPairingCode())
    }
}
