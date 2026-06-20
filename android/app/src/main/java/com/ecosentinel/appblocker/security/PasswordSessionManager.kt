package com.ecosentinel.appblocker.security

object PasswordSessionManager {

    private val unlockedPackages = mutableSetOf<String>()
    private var lastForegroundPackage: String? = null

    @Synchronized
    fun onForegroundChanged(packageName: String?, unlockMode: PasswordUnlockMode) {
        if (unlockMode == PasswordUnlockMode.EVERY_LAUNCH &&
            lastForegroundPackage != null &&
            lastForegroundPackage != packageName
        ) {
            unlockedPackages.remove(lastForegroundPackage)
        }
        lastForegroundPackage = packageName
    }

    @Synchronized
    fun unlock(packageName: String) {
        unlockedPackages.add(packageName)
    }

    @Synchronized
    fun isUnlocked(packageName: String): Boolean = unlockedPackages.contains(packageName)

    @Synchronized
    fun clearAll() {
        unlockedPackages.clear()
        lastForegroundPackage = null
    }
}
