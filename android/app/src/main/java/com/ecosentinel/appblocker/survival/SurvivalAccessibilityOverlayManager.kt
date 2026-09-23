package com.ecosentinel.appblocker.survival

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.WindowManager
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.databinding.ActivitySurvivalAccessibilityOverlayBinding
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.ui.SurvivalAccessibilityOverlayActivity
import com.ecosentinel.appblocker.util.PermissionHelper

object SurvivalAccessibilityOverlayManager {

    private const val SETTINGS_PACKAGE = "com.android.settings"
    private val RECHECK_DELAYS_MS = longArrayOf(1_500L, 4_000L, 8_000L, 15_000L, 30_000L)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: android.view.View? = null

    @Volatile
    private var recheckGeneration = 0

    fun sync(context: Context, snapshot: AppHealthSnapshot) {
        if (SurvivalSettings.shouldShowAccessibilityOverlay(context, snapshot)) {
            show(context)
        } else {
            hide(context)
        }
    }

    fun show(context: Context) {
        val appContext = context.applicationContext
        if (!PermissionHelper.canDrawOverlays(appContext)) {
            SurvivalAccessibilityOverlayActivity.launch(appContext)
            return
        }

        mainHandler.post {
            if (overlayView != null) {
                return@post
            }
            if (!SurvivalSettings.shouldShowAccessibilityOverlay(appContext, AppHealthChecker.check(appContext))) {
                return@post
            }
            if (isSystemSettingsForeground(appContext)) {
                scheduleRechecks(appContext)
                return@post
            }

            val themedContext = ContextThemeWrapper(appContext, R.style.Theme_Appbllocker_BlockOverlay)
            val inflater = LayoutInflater.from(themedContext)
            val binding = ActivitySurvivalAccessibilityOverlayBinding.inflate(inflater)
            binding.btnOpenAccessibility.setOnClickListener {
                hide(appContext)
                scheduleRechecks(appContext)
                SurvivalAccessibilityOverlayActivity.launch(appContext, openSettings = true)
            }

            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(binding.root, params)
                overlayView = binding.root
            } catch (_: Exception) {
                SurvivalAccessibilityOverlayActivity.launch(appContext)
            }
        }
    }

    fun hide(context: Context) {
        mainHandler.post {
            hideInternal(context.applicationContext)
        }
    }

    fun scheduleRechecks(context: Context) {
        val appContext = context.applicationContext
        mainHandler.post {
            recheckGeneration += 1
            val generation = recheckGeneration
            RECHECK_DELAYS_MS.forEach { delayMs ->
                mainHandler.postDelayed({
                    if (generation != recheckGeneration) {
                        return@postDelayed
                    }
                    val snapshot = AppHealthChecker.check(appContext)
                    if (SurvivalSettings.shouldShowAccessibilityOverlay(appContext, snapshot)) {
                        show(appContext)
                    }
                }, delayMs)
            }
        }
    }

    private fun cancelRechecks() {
        mainHandler.post {
            recheckGeneration += 1
        }
    }

    private fun hideInternal(context: Context) {
        val view = overlayView ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(view)
        } catch (_: Exception) {
        } finally {
            overlayView = null
            if (!SurvivalSettings.shouldShowAccessibilityOverlay(context, AppHealthChecker.check(context))) {
                cancelRechecks()
            }
        }
    }

    private fun isSystemSettingsForeground(context: Context): Boolean {
        return runCatching {
            UsageTracker(context).getForegroundPackage() == SETTINGS_PACKAGE
        }.getOrDefault(false)
    }
}
