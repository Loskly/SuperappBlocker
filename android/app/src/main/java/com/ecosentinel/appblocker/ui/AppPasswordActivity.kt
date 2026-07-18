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
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.databinding.ActivityAppPasswordBinding
import com.ecosentinel.appblocker.databinding.ItemPasswordAppBinding
import com.ecosentinel.appblocker.security.AppPasswordStore
import com.ecosentinel.appblocker.security.PasswordUnlockMode
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AppPasswordActivity : AppCompatActivity(), SetPasswordDialog.Listener {

    private lateinit var binding: ActivityAppPasswordBinding
    private lateinit var passwordStore: AppPasswordStore
    private lateinit var adapter: PasswordProtectedAppAdapter

    private val addAppLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            Toast.makeText(this, R.string.app_password_app_added, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        passwordStore = AppPasswordStore(this)
        adapter = PasswordProtectedAppAdapter { packageName ->
            lifecycleScope.launch {
                AppDatabase.getInstance(this@AppPasswordActivity)
                    .passwordProtectedAppDao()
                    .delete(packageName)
            }
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSetPassword.setOnClickListener {
            SetPasswordDialog.newInstance().show(supportFragmentManager, "set_password")
        }
        binding.btnAddApp.setOnClickListener {
            if (!passwordStore.hasPassword()) {
                Toast.makeText(this, R.string.app_password_set_first, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            addAppLauncher.launch(Intent(this, AddPasswordAppActivity::class.java))
        }

        binding.protectedAppsRecyclerView.prepareForScrollParent(this)
        binding.protectedAppsRecyclerView.adapter = adapter

        setupUnlockModeSelection()

        lifecycleScope.launch {
            AppDatabase.getInstance(this@AppPasswordActivity)
                .passwordProtectedAppDao()
                .observeAll()
                .collectLatest { entities ->
                    val rows = entities.map { entity ->
                        PasswordProtectedAppRow(
                            packageName = entity.packageName,
                            label = InstalledAppsHelper.getAppLabel(this@AppPasswordActivity, entity.packageName)
                        )
                    }
                    adapter.submitListRemeasure(binding.protectedAppsRecyclerView, rows)
                    binding.emptyAppsText.visibility =
                        if (rows.isEmpty()) View.VISIBLE else View.GONE
                }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePasswordStatus()
        updateUnlockModeSelection()
    }

    private fun setupUnlockModeSelection() {
        binding.unlockModeGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioUntilScreenOff -> PasswordUnlockMode.UNTIL_SCREEN_OFF
                else -> PasswordUnlockMode.EVERY_LAUNCH
            }
            if (passwordStore.getUnlockMode() != mode) {
                passwordStore.setUnlockMode(mode)
            }
        }
    }

    private fun updateUnlockModeSelection() {
        binding.unlockModeGroup.setOnCheckedChangeListener(null)
        when (passwordStore.getUnlockMode()) {
            PasswordUnlockMode.UNTIL_SCREEN_OFF -> binding.radioUntilScreenOff.isChecked = true
            PasswordUnlockMode.EVERY_LAUNCH -> binding.radioEveryLaunch.isChecked = true
        }
        setupUnlockModeSelection()
    }

    override fun onPasswordSaved() {
        updatePasswordStatus()
        Toast.makeText(this, R.string.app_password_saved, Toast.LENGTH_SHORT).show()
    }

    private fun updatePasswordStatus() {
        if (passwordStore.hasPassword()) {
            binding.passwordStatusText.text = getString(R.string.app_password_status_set)
            binding.btnSetPassword.text = getString(R.string.app_password_change)
        } else {
            binding.passwordStatusText.text = getString(R.string.app_password_status_not_set)
            binding.btnSetPassword.text = getString(R.string.app_password_set)
        }
    }
}

data class PasswordProtectedAppRow(
    val packageName: String,
    val label: String
)

private class PasswordProtectedAppAdapter(
    private val onRemove: (String) -> Unit
) : ListAdapter<PasswordProtectedAppRow, PasswordProtectedAppAdapter.ViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<PasswordProtectedAppRow>() {
        override fun areItemsTheSame(
            oldItem: PasswordProtectedAppRow,
            newItem: PasswordProtectedAppRow
        ) = oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(
            oldItem: PasswordProtectedAppRow,
            newItem: PasswordProtectedAppRow
        ) = oldItem == newItem
    }

    class ViewHolder(
        private val binding: ItemPasswordAppBinding,
        private val onRemove: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PasswordProtectedAppRow) {
            binding.appNameText.text = row.label
            binding.packageText.text = row.packageName
            binding.appIcon.setImageDrawable(
                InstalledAppsHelper.getAppIcon(binding.root.context, row.packageName)
            )
            binding.btnRemove.setOnClickListener { onRemove(row.packageName) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPasswordAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onRemove)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
