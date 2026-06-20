package com.ecosentinel.appblocker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.ecosentinel.appblocker.databinding.ActivityMainBinding
import com.ecosentinel.appblocker.service.MonitorBootstrap
import com.ecosentinel.appblocker.ui.FeaturesFragment
import com.ecosentinel.appblocker.ui.HomeFragment
import com.ecosentinel.appblocker.ui.LimitsFragment
import com.ecosentinel.appblocker.ui.StatsFragment
import com.ecosentinel.appblocker.ui.TodoFragment
import com.ecosentinel.appblocker.util.PermissionHelper
import com.ecosentinel.appblocker.util.WindowInsetsHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var homeFragment: HomeFragment
    private lateinit var limitsFragment: LimitsFragment
    private lateinit var statsFragment: StatsFragment
    private lateinit var todoFragment: TodoFragment
    private lateinit var featuresFragment: FeaturesFragment

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        MonitorBootstrap.ensureMonitoring(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.enableEdgeToEdge(this)
        WindowInsetsHelper.setupMainActivityInsets(binding.fragmentContainer, binding.bottomNav)

        if (savedInstanceState == null) {
            homeFragment = HomeFragment()
            limitsFragment = LimitsFragment()
            statsFragment = StatsFragment()
            todoFragment = TodoFragment()
            featuresFragment = FeaturesFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, homeFragment, TAG_HOME)
                .add(R.id.fragmentContainer, limitsFragment, TAG_LIMITS)
                .add(R.id.fragmentContainer, statsFragment, TAG_STATS)
                .add(R.id.fragmentContainer, todoFragment, TAG_TODO)
                .add(R.id.fragmentContainer, featuresFragment, TAG_FEATURES)
                .hide(limitsFragment)
                .hide(statsFragment)
                .hide(todoFragment)
                .hide(featuresFragment)
                .commit()
            binding.bottomNav.selectedItemId = R.id.nav_home
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag(TAG_HOME) as HomeFragment
            limitsFragment = supportFragmentManager.findFragmentByTag(TAG_LIMITS) as LimitsFragment
            statsFragment = supportFragmentManager.findFragmentByTag(TAG_STATS) as StatsFragment
            todoFragment = supportFragmentManager.findFragmentByTag(TAG_TODO) as TodoFragment
            featuresFragment = supportFragmentManager.findFragmentByTag(TAG_FEATURES) as FeaturesFragment
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showTab(homeFragment)
                R.id.nav_limits -> showTab(limitsFragment)
                R.id.nav_stats -> showTab(statsFragment)
                R.id.nav_todo -> showTab(todoFragment)
                R.id.nav_features -> showTab(featuresFragment)
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        requestNotificationPermissionIfNeeded()
        MonitorBootstrap.ensureMonitoring(this)
    }

    override fun onResume() {
        super.onResume()
        MonitorBootstrap.ensureMonitoring(this)
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun showTab(selected: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        allTabs().forEach { fragment ->
            if (fragment === selected) {
                transaction.show(fragment)
            } else {
                transaction.hide(fragment)
            }
        }
        transaction.commit()
    }

    private fun allTabs(): List<Fragment> =
        listOf(homeFragment, limitsFragment, statsFragment, todoFragment, featuresFragment)

    companion object {
        private const val TAG_HOME = "home"
        private const val TAG_LIMITS = "limits"
        private const val TAG_STATS = "stats"
        private const val TAG_TODO = "todo"
        private const val TAG_FEATURES = "features"
    }
}
