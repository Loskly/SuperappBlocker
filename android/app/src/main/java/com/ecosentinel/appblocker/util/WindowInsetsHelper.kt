package com.ecosentinel.appblocker.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.ecosentinel.appblocker.MainActivity
import com.ecosentinel.appblocker.ui.BlockOverlayActivity
import com.ecosentinel.appblocker.ui.PasswordOverlayActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object WindowInsetsHelper {

    private val insetTypes =
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()

    fun enableEdgeToEdge(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    }

    fun applyContentInsets(view: View) {
        val initialLeft = view.paddingLeft
        val initialTop = view.paddingTop
        val initialRight = view.paddingRight
        val initialBottom = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { target, windowInsets ->
            val insets = windowInsets.getInsets(insetTypes)
            target.setPadding(
                initialLeft + insets.left,
                initialTop + insets.top,
                initialRight + insets.right,
                initialBottom + insets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun setupMainActivityInsets(contentContainer: View, bottomNavigation: View) {
        val containerInitialLeft = contentContainer.paddingLeft
        val containerInitialTop = contentContainer.paddingTop
        val containerInitialRight = contentContainer.paddingRight
        val containerInitialBottom = contentContainer.paddingBottom

        val navInitialLeft = bottomNavigation.paddingLeft
        val navInitialTop = bottomNavigation.paddingTop
        val navInitialRight = bottomNavigation.paddingRight
        val navInitialBottom = bottomNavigation.paddingBottom

        val root = contentContainer.rootView
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val insets = windowInsets.getInsets(insetTypes)
            contentContainer.setPadding(
                containerInitialLeft + insets.left,
                containerInitialTop + insets.top,
                containerInitialRight + insets.right,
                containerInitialBottom
            )
            bottomNavigation.setPadding(
                navInitialLeft + insets.left,
                navInitialTop,
                navInitialRight + insets.right,
                navInitialBottom + insets.bottom
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                when (activity) {
                    is BlockOverlayActivity,
                    is PasswordOverlayActivity,
                    is MainActivity -> return
                }
                enableEdgeToEdge(activity)
                activity.window.decorView.post {
                    val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
                        ?.getChildAt(0)
                        ?: return@post
                    applyContentInsets(contentRoot)
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
