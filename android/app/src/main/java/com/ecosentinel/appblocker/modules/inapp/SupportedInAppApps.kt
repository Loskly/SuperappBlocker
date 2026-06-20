package com.ecosentinel.appblocker.modules.inapp

object SupportedInAppApps {

    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    fun isSupported(packageName: String): Boolean {
        return packageName == YOUTUBE || packageName == INSTAGRAM
    }
}
