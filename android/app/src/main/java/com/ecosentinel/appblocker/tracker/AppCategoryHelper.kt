package com.ecosentinel.appblocker.tracker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ecosentinel.appblocker.util.InstalledAppsHelper

object AppCategoryHelper {

    fun categoryIdForPackage(context: Context, packageName: String): String {
        return categoryForPackage(context, packageName).id
    }

    fun categoryName(context: Context, packageName: String): String {
        return categoryForPackage(context, packageName).displayName
    }

    fun displayNameForCategoryId(categoryId: String): String {
        return AppCategory.fromId(categoryId)?.displayName ?: categoryId
    }

    fun belongsToCategory(context: Context, packageName: String, categoryId: String): Boolean {
        return categoryIdForPackage(context, packageName) == categoryId
    }

    fun aggregateUsageForCategory(
        context: Context,
        usageByPackage: Map<String, Long>,
        categoryId: String
    ): Long {
        return usageByPackage.entries.sumOf { (packageName, millis) ->
            if (millis <= 0L) {
                0L
            } else if (belongsToCategory(context, packageName, categoryId)) {
                millis
            } else {
                0L
            }
        }
    }

    fun isPreinstalledSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        } catch (_: Exception) {
            false
        }
    }

    fun filterUsageForDisplay(
        context: Context,
        usageByPackage: Map<String, Long>,
        includeHiddenSystemComponents: Boolean
    ): Map<String, Long> {
        return usageByPackage.filter { (packageName, millis) ->
            millis > 0L &&
                InstalledAppsHelper.shouldShowInStats(context, packageName, includeHiddenSystemComponents)
        }
    }

    fun hiddenSystemUsageMillis(
        context: Context,
        usageByPackage: Map<String, Long>
    ): Long {
        return usageByPackage.entries.sumOf { (packageName, millis) ->
            if (millis <= 0L) {
                0L
            } else if (InstalledAppsHelper.isHiddenFromStatsByDefault(context, packageName)) {
                millis
            } else {
                0L
            }
        }
    }

    fun groupByCategory(
        context: Context,
        usageByPackage: Map<String, Long>,
        includeHiddenSystemComponents: Boolean = false,
        hiddenSystemCategoryName: String = "Системные компоненты"
    ): List<CategoryUsage> {
        val grouped = linkedMapOf<String, Long>()
        usageByPackage.forEach { (packageName, millis) ->
            if (millis <= 0L) return@forEach
            if (!InstalledAppsHelper.shouldShowInStats(context, packageName, includeHiddenSystemComponents)) {
                return@forEach
            }
            val category = categoryName(context, packageName)
            grouped[category] = (grouped[category] ?: 0L) + millis
        }
        if (!includeHiddenSystemComponents) {
            val hiddenMillis = hiddenSystemUsageMillis(context, usageByPackage)
            if (hiddenMillis > 0L) {
                grouped[hiddenSystemCategoryName] =
                    (grouped[hiddenSystemCategoryName] ?: 0L) + hiddenMillis
            }
        }
        return grouped.entries
            .map { CategoryUsage(it.key, it.value) }
            .sortedByDescending { it.millis }
    }

    fun toAppDetails(
        context: Context,
        usageByPackage: Map<String, Long>,
        includeHiddenSystemComponents: Boolean
    ): List<AppUsageDetail> {
        val filtered = filterUsageForDisplay(context, usageByPackage, includeHiddenSystemComponents)
        // Share is relative to the apps actually shown, so the visible rows add up to ~100% and the
        // top app's progress bar reflects its weight among them (rather than being diluted by the
        // hidden/system time the user isn't looking at).
        val visibleTotal = filtered.values.sum().coerceAtLeast(1L)
        return filtered.map { (pkg, ms) ->
            AppUsageDetail(
                packageName = pkg,
                label = InstalledAppsHelper.getAppLabel(context, pkg),
                usedMillis = ms,
                shareOfTotal = ms.toFloat() / visibleTotal.toFloat(),
                isSystemApp = isPreinstalledSystemApp(context, pkg) ||
                    InstalledAppsHelper.isHiddenFromStatsByDefault(context, pkg)
            )
        }.sortedByDescending { it.usedMillis }
    }

    private fun categoryForPackage(context: Context, packageName: String): AppCategory {
        val pm = context.packageManager
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            categoryForApp(appInfo)
        } catch (_: PackageManager.NameNotFoundException) {
            AppCategory.OTHER
        }
    }

    @Suppress("DEPRECATION")
    private fun categoryForApp(appInfo: ApplicationInfo): AppCategory {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
                ApplicationInfo.CATEGORY_AUDIO -> AppCategory.AUDIO
                ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
                ApplicationInfo.CATEGORY_IMAGE -> AppCategory.IMAGE
                ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
                ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS
                ApplicationInfo.CATEGORY_MAPS -> AppCategory.MAPS
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> AppCategory.ACCESSIBILITY
                else -> inferCategory(appInfo.packageName)
            }
        }
        return inferCategory(appInfo.packageName)
    }

    private fun inferCategory(packageName: String): AppCategory {
        val lower = packageName.lowercase()
        return when {
            "game" in lower || lower.startsWith("com.supercell") -> AppCategory.GAME
            lower.contains("youtube") || lower.contains("video") || lower.contains("tv") -> AppCategory.VIDEO
            lower.contains("chrome") || lower.contains("browser") -> AppCategory.BROWSER
            lower.contains("telegram") || lower.contains("whatsapp") || lower.contains("vk.") -> AppCategory.SOCIAL
            lower.contains("music") || lower.contains("spotify") -> AppCategory.AUDIO
            else -> AppCategory.OTHER
        }
    }
}
