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
import com.ecosentinel.appblocker.databinding.FragmentSettingsBinding
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.service.UsageMonitorService
import com.ecosentinel.appblocker.sync.DashboardChangeStore
import com.ecosentinel.appblocker.sync.DeviceTokenStore
import com.ecosentinel.appblocker.sync.PairingApi
import com.ecosentinel.appblocker.sync.SyncApi
import com.ecosentinel.appblocker.util.PermissionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var tokenStore: DeviceTokenStore
    private lateinit var changeStore: DashboardChangeStore
    private var pairingPollJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tokenStore = DeviceTokenStore(requireContext())
        changeStore = DashboardChangeStore(requireContext())
        binding.serverUrlInput.setText(tokenStore.getApiBaseUrl(com.ecosentinel.appblocker.BuildConfig.API_BASE_URL))

        binding.btnDashboardChanges.setOnClickListener {
            showDashboardChanges()
        }
        binding.btnCheckPermissions.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionsActivity::class.java))
        }
        binding.btnSync.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    SyncApi(requireContext()).sync()
                }
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                refreshStatus()
            }
        }
        binding.btnSaveServerUrl.setOnClickListener {
            saveServerUrlFromInput()
            Toast.makeText(requireContext(), getString(R.string.remote_server_saved), Toast.LENGTH_SHORT).show()
            refreshStatus()
        }
        binding.btnStartPairing.setOnClickListener {
            startPairingFlow()
        }
        binding.btnCheckPairing.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                checkPairingOnce(showPendingToast = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorBootstrap.ensureMonitoring(requireContext())
        refreshStatus()
        startPairingStatusPollingIfNeeded()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pairingPollJob?.cancel()
        pairingPollJob = null
        _binding = null
    }

    private fun saveServerUrlFromInput() {
        val baseUrl = binding.serverUrlInput.text?.toString().orEmpty()
        tokenStore.saveApiBaseUrl(baseUrl)
    }

    private fun startPairingFlow() {
        viewLifecycleOwner.lifecycleScope.launch {
            saveServerUrlFromInput()
            setPairingButtonsEnabled(false)
            val result = withContext(Dispatchers.IO) {
                PairingApi(requireContext()).startPairing()
            }
            setPairingButtonsEnabled(true)
            Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            refreshStatus()
            if (result.pending) {
                startPairingStatusPollingIfNeeded()
            }
        }
    }

    private suspend fun checkPairingOnce(showPendingToast: Boolean): PairingApi.PairingActionResult {
        val result = withContext(Dispatchers.IO) {
            PairingApi(requireContext()).checkPairingStatus()
        }
        refreshStatus()
        when {
            result.success -> {
                Toast.makeText(requireContext(), getString(R.string.remote_pairing_connected), Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.IO) {
                    SyncApi(requireContext()).sync()
                }
                refreshStatus()
            }
            result.pending && showPendingToast -> {
                Toast.makeText(requireContext(), getString(R.string.remote_pairing_waiting_dashboard), Toast.LENGTH_SHORT).show()
            }
            !result.pending -> {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
        return result
    }

    private fun startPairingStatusPollingIfNeeded() {
        if (tokenStore.isPaired() || tokenStore.getPendingPairingCode().isNullOrBlank()) {
            pairingPollJob?.cancel()
            pairingPollJob = null
            return
        }
        if (pairingPollJob?.isActive == true) {
            return
        }
        pairingPollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (
                isActive &&
                !tokenStore.isPaired() &&
                !tokenStore.getPendingPairingCode().isNullOrBlank()
            ) {
                delay(PAIRING_POLL_INTERVAL_MS)
                val result = checkPairingOnce(showPendingToast = false)
                if (!result.pending) {
                    pairingPollJob = null
                    break
                }
            }
        }
    }

    private fun setPairingButtonsEnabled(enabled: Boolean) {
        binding.btnStartPairing.isEnabled = enabled
        binding.btnCheckPairing.isEnabled = enabled
        binding.btnSaveServerUrl.isEnabled = enabled
    }

    private fun showDashboardChanges() {
        val changes = changeStore.getChanges()
        val message = if (changes.isEmpty()) {
            getString(R.string.dashboard_changes_empty)
        } else {
            changes.joinToString(separator = "\n\n") { change ->
                "${change.createdAt}\n${change.summary}"
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dashboard_changes_title)
            .setMessage(message)
            .setPositiveButton(R.string.dashboard_changes_close, null)
            .show()
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
        if (!binding.serverUrlInput.hasFocus()) {
            binding.serverUrlInput.setText(tokenStore.getApiBaseUrl(com.ecosentinel.appblocker.BuildConfig.API_BASE_URL))
        }
        val pendingCode = tokenStore.getPendingPairingCode()
        binding.pairingCodeText.visibility = if (!pendingCode.isNullOrBlank() && !tokenStore.isPaired()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.pairingCodeText.text = getString(R.string.pairing_code_value, pendingCode ?: tokenStore.getPairingCode())
        binding.remotePairingStatus.text = getString(
            R.string.remote_pairing_status_value,
            when {
                tokenStore.isPaired() -> getString(R.string.remote_pairing_status_paired)
                tokenStore.getPendingPairingCode() != null -> getString(R.string.remote_pairing_status_pending)
                else -> getString(R.string.remote_pairing_status_not_paired)
            }
        )
        binding.pairingExpiresText.text = tokenStore.getPendingPairingExpiresAt()?.let {
            getString(R.string.remote_pairing_expires_value, it)
        } ?: getString(R.string.remote_pairing_expires_empty)
    }

    companion object {
        private const val PAIRING_POLL_INTERVAL_MS = 3_000L
    }
}
