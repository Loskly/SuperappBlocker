package com.ecosentinel.appblocker.ui

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.util.PermissionHelper
import kotlinx.coroutines.launch

object BlockOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: android.view.View? = null

    @Volatile
    private var attachedPackage: String? = null

    fun show(context: Context, packageName: String, reason: String) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            BlockOverlayActivity.launch(context, packageName, reason)
            return
        }

        mainHandler.post {
            val appContext = context.applicationContext
            if (overlayView != null && attachedPackage == packageName) {
                updateContent(appContext, packageName, reason)
                return@post
            }

            hideInternal(appContext)

            val themedContext = ContextThemeWrapper(appContext, R.style.Theme_Appbllocker_BlockOverlay)
            val inflater = LayoutInflater.from(themedContext)
            val view = inflater.inflate(R.layout.overlay_block, null)
            updateViewContent(appContext, view, packageName, reason)

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
                windowManager.addView(view, params)
                overlayView = view
                attachedPackage = packageName
            } catch (_: Exception) {
                BlockOverlayActivity.launch(context, packageName, reason)
            }
        }
    }

    fun hide(context: Context) {
        mainHandler.post {
            hideInternal(context.applicationContext)
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
            attachedPackage = null
        }
    }

    private fun updateContent(context: Context, packageName: String, reason: String) {
        val view = overlayView ?: return
        updateViewContent(context, view, packageName, reason)
    }

    private fun updateViewContent(context: Context, view: android.view.View, packageName: String, reason: String) {
        val label = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }

        view.findViewById<TextView>(R.id.blockedAppName).text = label
        view.findViewById<TextView>(R.id.blockMessage).text =
            BlockOverlayTexts.messageForReason(context, reason)

        val resetView = view.findViewById<TextView>(R.id.resetTimeText)
        resetView.text = BlockOverlayTexts.footerForReason(context, packageName, reason)

        val motivationView = view.findViewById<TextView>(R.id.motivationText)
        motivationView.visibility = android.view.View.GONE // Default to gone while loading
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val db = com.ecosentinel.appblocker.data.AppDatabase.getInstance(context)
                val repo = com.ecosentinel.appblocker.data.repository.MotivationRepository(db.motivationDao())
                val text = repo.getRandomMotivation()
                motivationView.text = text
                motivationView.visibility = android.view.View.VISIBLE
            } catch (e: Exception) {
                // Ignore if DB fails
            }
        }

        view.findViewById<android.view.View>(R.id.btnGoHome).setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            hide(context)
        }
    }
}
