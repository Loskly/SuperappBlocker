package com.ecosentinel.appblocker.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.PasswordProtectedAppEntity
import com.ecosentinel.appblocker.databinding.ActivityAddAppBinding
import com.ecosentinel.appblocker.util.InstalledApp
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddPasswordAppActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAppBinding
    private lateinit var adapter: AddAppAdapter
    private var allApps: List<InstalledApp> = emptyList()
    private var protectedPackages: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddAppBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.screenTitle.text = getString(R.string.app_password_add_app)

        adapter = AddAppAdapter { app ->
            if (protectedPackages.contains(app.packageName)) {
                Toast.makeText(this, R.string.app_password_already_added, Toast.LENGTH_SHORT).show()
                return@AddAppAdapter
            }
            lifecycleScope.launch {
                AppDatabase.getInstance(this@AddPasswordAppActivity)
                    .passwordProtectedAppDao()
                    .insert(
                        PasswordProtectedAppEntity(
                            packageName = app.packageName,
                            addedAtMillis = System.currentTimeMillis()
                        )
                    )
                Toast.makeText(this@AddPasswordAppActivity, R.string.app_password_app_added, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.appsRecyclerView.adapter = adapter
        loadApps()
    }

    private fun loadApps() {
        binding.loadingText.visibility = View.VISIBLE
        binding.emptyAppsText.visibility = View.GONE

        lifecycleScope.launch {
            val dao = AppDatabase.getInstance(this@AddPasswordAppActivity).passwordProtectedAppDao()
            protectedPackages = withContext(Dispatchers.IO) {
                dao.getAll().map { it.packageName }.toSet()
            }
            allApps = withContext(Dispatchers.IO) {
                InstalledAppsHelper.getAllInstalledApps(this@AddPasswordAppActivity)
            }
            binding.loadingText.visibility = View.GONE
            binding.emptyAppsText.visibility = if (allApps.isEmpty()) View.VISIBLE else View.GONE
            binding.searchInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    applyFilter(s?.toString().orEmpty())
                }
            })
            applyFilter("")
        }
    }

    private fun applyFilter(queryRaw: String) {
        val query = queryRaw.trim().lowercase()
        val filtered = allApps.filter {
            val matchesQuery = query.isEmpty() ||
                it.label.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
            matchesQuery && !protectedPackages.contains(it.packageName)
        }
        adapter.submitList(filtered)
        binding.emptyAppsText.visibility =
            if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
