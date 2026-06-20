package com.ecosentinel.appblocker.modules.apppassword

import android.content.Context
import com.ecosentinel.appblocker.BuildConfig
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.security.AppPasswordStore
import com.ecosentinel.appblocker.security.PasswordSessionManager
import kotlinx.coroutines.runBlocking

class AppPasswordModule(context: Context) {

    private val appContext = context.applicationContext
    private val passwordStore = AppPasswordStore(appContext)
    private val dao = AppDatabase.getInstance(appContext).passwordProtectedAppDao()

    private val alwaysAllowed = setOf(
        BuildConfig.APPLICATION_ID,
        "com.android.systemui"
    )

    fun shouldRequirePassword(foregroundPackage: String?): Boolean {
        if (!passwordStore.hasPassword()) return false
        val packageName = foregroundPackage ?: return false
        if (packageName in alwaysAllowed) return false
        if (PasswordSessionManager.isUnlocked(packageName)) return false
        return runBlocking { dao.isProtected(packageName) > 0 }
    }
}
