package com.ecosentinel.appblocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process

data class InstalledApp(
    val packageName: String,
    val label: String
)

object InstalledAppsHelper {

    @Volatile
    private var cachedLauncherPackages: Set<String>? = null

    private val blockedPackagePrefixes = listOf(
        "com.android.providers.",
        "com.android.server.",
        "com.android.inputmethod.",
        "com.android.internal.",
        "com.android.localtransport",
        "com.android.keychain",
        "com.android.shell.",
        "com.android.pacprocessor",
        "com.android.mms.service",
        "com.android.nfc",
        "com.android.dynsystem",
        "com.android.hotspot2.",
        "com.android.carrierconfig",
        "com.android.imsserviceentitlement",
        "com.android.wallpaperbackup",
        "com.android.statementservice",
        "com.android.proxyhandler",
        "com.android.sharedstoragebackup",
        "com.android.externalstorage",
        "com.android.bluetoothmidiservice",
        "com.android.location.fused",
        "com.google.android.onetimeinitializer",
        "com.google.android.configupdater",
        "com.google.android.partnersetup",
        "com.google.android.networkstack.",
        "com.google.android.cellbroadcast",
        "com.qualcomm.",
        "com.qti.",
    )

    private val blockedPackages = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.packageinstaller",
        "com.google.android.ext.services",
        "com.google.android.ext.shared",
        "com.android.htmlviewer",
        "com.android.certinstaller",
        "com.android.egg",
        "com.android.wallpaper",
        "com.android.wallpapercropper",
        "com.android.traceur",
        "com.android.emergency",
        "com.android.stk",
        "com.android.managedprovisioning",
        "com.android.companiondevicemanager",
        "com.google.android.modulemetadata",
        "com.google.android.permissioncontroller",
    )

    private val blockedLabelKeywords = listOf(
        "main components",
        "основные компоненты",
        "configupdater",
        "one-time initialization",
        "partner setup",
        "pac processor",
        "cert installer",
        "key chain",
        "local transport",
        "traceur",
        "sim toolkit",
        "module metadata",
        "network stack",
        "cell broadcast",
        "intent resolver",
        "wallpaper backup",
        "backup transport",
        "managed provisioning",
        "permission controller",
        "system ui",
        "android system",
        "системный ui",
        "установщик пакетов",
    )

    private val blockedActivityKeywords = listOf(
        "MainComponent",
        "Components",
        "ComponentDiscovery",
        "DevelopmentSettings",
        "TestingSettings",
        "ManagedProvisioning",
        "ModuleMetadata",
        "SecretCode",
        "TestActivity",
    )

    fun getAllInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val selfPackage = context.packageName
        val launcherPackages = getLaunchablePackageNames(context, pm)

        return queryInstalledApplications(pm)
            .asSequence()
            .filter { it.packageName != selfPackage }
            .filter { it.enabled }
            .filter { !isBlockedPackage(it.packageName) }
            .filter { isSelectableApp(it, launcherPackages) }
            .mapNotNull { appInfo ->
                val label = getAppLabelOrNull(pm, appInfo) ?: return@mapNotNull null
                if (isBlockedLabel(label)) return@mapNotNull null
                InstalledApp(packageName = appInfo.packageName, label = label)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName.lowercase() }))
            .toList()
    }

    @Deprecated("Use getAllInstalledApps")
    fun getLaunchableApps(context: Context): List<InstalledApp> = getAllInstalledApps(context)

    fun getAppLabel(context: Context, packageName: String): String {
        return getAppLabelOrNull(context.packageManager, packageName) ?: packageName
    }

    fun getAppIcon(context: Context, packageName: String) =
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            context.packageManager.defaultActivityIcon
        }

    /**
     * Packages that should never appear in usage stats (providers, System UI shell, etc.).
     */
    fun isBlockedStatsPackage(packageName: String): Boolean = isBlockedPackage(packageName)

    /**
     * Hidden from stats by default: blocked components and non-launcher preinstalled system apps.
     * User-facing system apps (Settings, Phone, updated Chrome/YouTube) remain visible.
     */
    fun isHiddenFromStatsByDefault(context: Context, packageName: String): Boolean {
        if (isBlockedPackage(packageName)) {
            return true
        }
        return try {
            val pm = context.packageManager
            val appInfo = getApplicationInfo(pm, packageName)
            if (isUserInstalledApp(appInfo)) {
                return false
            }
            if (isUpdatedSystemApp(appInfo)) {
                return false
            }
            !getLaunchablePackageNamesCached(context, pm).contains(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun shouldShowInStats(
        context: Context,
        packageName: String,
        includeHiddenSystemComponents: Boolean
    ): Boolean {
        if (includeHiddenSystemComponents) {
            return true
        }
        return !isHiddenFromStatsByDefault(context, packageName)
    }

    private fun isSelectableApp(appInfo: ApplicationInfo, launcherPackages: Set<String>): Boolean {
        if (isUserInstalledApp(appInfo)) {
            return true
        }

        if (isUpdatedSystemApp(appInfo)) {
            return true
        }

        return launcherPackages.contains(appInfo.packageName)
    }

    private fun isUserInstalledApp(appInfo: ApplicationInfo): Boolean {
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        return !isSystem
    }

    private fun isUpdatedSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    private fun isBlockedPackage(packageName: String): Boolean {
        if (blockedPackages.contains(packageName)) {
            return true
        }
        return blockedPackagePrefixes.any { packageName.startsWith(it) }
    }

    private fun isBlockedLabel(label: String): Boolean {
        val normalized = label.trim().lowercase()
        if (normalized.isEmpty()) {
            return true
        }
        return blockedLabelKeywords.any { normalized.contains(it) }
    }

    private fun isBlockedLauncherActivity(className: String, activityLabel: String?): Boolean {
        if (blockedActivityKeywords.any { className.contains(it, ignoreCase = true) }) {
            return true
        }
        val label = activityLabel?.trim().orEmpty()
        return label.isNotEmpty() && isBlockedLabel(label)
    }

    private fun getAppLabelOrNull(pm: PackageManager, appInfo: ApplicationInfo): String? {
        return try {
            pm.getApplicationLabel(appInfo).toString().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun getAppLabelOrNull(pm: PackageManager, packageName: String): String? {
        return try {
            val appInfo = getApplicationInfo(pm, packageName)
            getAppLabelOrNull(pm, appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun getApplicationInfo(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
    }

    private fun queryInstalledApplications(pm: PackageManager): List<ApplicationInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
    }

    private fun getLaunchablePackageNamesCached(context: Context, pm: PackageManager): Set<String> {
        cachedLauncherPackages?.let { return it }
        return getLaunchablePackageNames(context, pm).also { cachedLauncherPackages = it }
    }

    private fun getLaunchablePackageNames(context: Context, pm: PackageManager): Set<String> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps != null) {
            try {
                return launcherApps.getActivityList(null, Process.myUserHandle())
                    .filter { activity -> !isBlockedLauncherActivity(activity.componentName.className, activity.label?.toString()) }
                    .map { it.applicationInfo.packageName }
                    .filter { !isBlockedPackage(it) }
                    .toSet()
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        return queryLauncherActivities(pm)
            .filter { resolveInfo -> !isBlockedLauncherActivity(resolveInfo.activityInfo.name, resolveInfo.loadLabel(pm).toString()) }
            .map { it.activityInfo.packageName }
            .filter { !isBlockedPackage(it) }
            .toSet()
    }

    private fun queryLauncherActivities(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.filter { resolveInfo ->
            resolveInfo.activityInfo.enabled && resolveInfo.activityInfo.exported
        }
    }
}
