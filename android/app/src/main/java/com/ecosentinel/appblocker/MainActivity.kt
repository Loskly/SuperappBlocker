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
import com.ecosentinel.appblocker.ui.CaloriesFragment
import com.ecosentinel.appblocker.ui.FeaturesFragment
import com.ecosentinel.appblocker.ui.HomeFragment
import com.ecosentinel.appblocker.ui.SettingsFragment
import com.ecosentinel.appblocker.ui.LimitsFragment
import com.ecosentinel.appblocker.ui.StatsFragment
import com.ecosentinel.appblocker.ui.TodoFragment
import com.ecosentinel.appblocker.util.PermissionHelper
import com.ecosentinel.appblocker.util.WindowInsetsHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var homeFragment: HomeFragment
    private lateinit var settingsFragment: SettingsFragment
    private lateinit var limitsFragment: LimitsFragment
    private lateinit var statsFragment: StatsFragment
    private lateinit var todoFragment: TodoFragment
    private lateinit var caloriesFragment: CaloriesFragment
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
            settingsFragment = SettingsFragment()
            limitsFragment = LimitsFragment()
            statsFragment = StatsFragment()
            todoFragment = TodoFragment()
            caloriesFragment = CaloriesFragment()
            featuresFragment = FeaturesFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, homeFragment, TAG_HOME)
                .add(R.id.fragmentContainer, settingsFragment, TAG_SETTINGS)
                .add(R.id.fragmentContainer, limitsFragment, TAG_LIMITS)
                .add(R.id.fragmentContainer, statsFragment, TAG_STATS)
                .add(R.id.fragmentContainer, todoFragment, TAG_TODO)
                .add(R.id.fragmentContainer, caloriesFragment, TAG_CALORIES)
                .add(R.id.fragmentContainer, featuresFragment, TAG_FEATURES)
                .hide(settingsFragment)
                .hide(limitsFragment)
                .hide(statsFragment)
                .hide(todoFragment)
                .hide(caloriesFragment)
                .hide(featuresFragment)
                .commit()
            binding.bottomNav.selectItem(R.id.nav_home, notify = false)
        } else {
            homeFragment = findOrCreateFragment(TAG_HOME) { HomeFragment() } as HomeFragment
            settingsFragment = findOrCreateFragment(TAG_SETTINGS) { SettingsFragment() } as SettingsFragment
            limitsFragment = findOrCreateFragment(TAG_LIMITS) { LimitsFragment() } as LimitsFragment
            statsFragment = findOrCreateFragment(TAG_STATS) { StatsFragment() } as StatsFragment
            todoFragment = findOrCreateFragment(TAG_TODO) { TodoFragment() } as TodoFragment
            caloriesFragment = findOrCreateFragment(TAG_CALORIES) { CaloriesFragment() } as CaloriesFragment
            featuresFragment = findOrCreateFragment(TAG_FEATURES) { FeaturesFragment() } as FeaturesFragment
            val restoredNavId = savedInstanceState.getInt(STATE_SELECTED_NAV_ID, R.id.nav_home)
            binding.bottomNav.selectItem(restoredNavId, notify = false)
            showTab(navIdToFragment(restoredNavId))
        }

        binding.bottomNav.setOnItemSelectedListener { itemId ->
            when (itemId) {
                R.id.nav_home -> showTab(homeFragment)
                R.id.nav_settings -> showTab(settingsFragment)
                R.id.nav_limits -> showTab(limitsFragment)
                R.id.nav_stats -> showTab(statsFragment)
                R.id.nav_todo -> showTab(todoFragment)
                R.id.nav_calories -> showTab(caloriesFragment)
                R.id.nav_features -> showTab(featuresFragment)
                else -> return@setOnItemSelectedListener false
            }
            true
        }

        requestNotificationPermissionIfNeeded()
        MonitorBootstrap.ensureMonitoring(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_NAV_ID, binding.bottomNav.selectedItemId())
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

    private fun findOrCreateFragment(tag: String, factory: () -> Fragment): Fragment {
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            return existing
        }
        val fragment = factory()
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, fragment, tag)
            .hide(fragment)
            .commitNow()
        return fragment
    }

    private fun navIdToFragment(navId: Int): Fragment {
        return when (navId) {
            R.id.nav_settings -> settingsFragment
            R.id.nav_limits -> limitsFragment
            R.id.nav_stats -> statsFragment
            R.id.nav_todo -> todoFragment
            R.id.nav_calories -> caloriesFragment
            R.id.nav_features -> featuresFragment
            else -> homeFragment
        }
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
        listOf(homeFragment, settingsFragment, limitsFragment, statsFragment, todoFragment, caloriesFragment, featuresFragment)

    companion object {
        private const val TAG_HOME = "home"
        private const val TAG_SETTINGS = "settings"
        private const val TAG_LIMITS = "limits"
        private const val TAG_STATS = "stats"
        private const val TAG_TODO = "todo"
        private const val TAG_CALORIES = "calories"
        private const val TAG_FEATURES = "features"
        private const val STATE_SELECTED_NAV_ID = "selected_nav_id"
    }
}
