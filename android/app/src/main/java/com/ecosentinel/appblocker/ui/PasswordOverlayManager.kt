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
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.security.AppPasswordStore
import com.ecosentinel.appblocker.security.PasswordSessionManager
import com.ecosentinel.appblocker.util.InstalledAppsHelper
import com.ecosentinel.appblocker.util.PermissionHelper
import com.google.android.material.textfield.TextInputEditText

object PasswordOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var overlayView: android.view.View? = null

    @Volatile
    private var attachedPackage: String? = null

    fun show(context: Context, packageName: String) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            PasswordOverlayActivity.launch(context, packageName)
            return
        }

        mainHandler.post {
            val appContext = context.applicationContext
            if (overlayView != null && attachedPackage == packageName) {
                return@post
            }

            hideInternal(appContext)

            val themedContext = ContextThemeWrapper(appContext, R.style.Theme_Appbllocker_BlockOverlay)
            val inflater = LayoutInflater.from(themedContext)
            val view = inflater.inflate(R.layout.overlay_password, null)
            bindView(appContext, view, packageName)

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
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )

            try {
                windowManager.addView(view, params)
                overlayView = view
                attachedPackage = packageName
                view.findViewById<TextInputEditText>(R.id.pinInput)?.requestFocus()
            } catch (_: Exception) {
                PasswordOverlayActivity.launch(context, packageName)
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

    private fun bindView(context: Context, view: android.view.View, packageName: String) {
        val label = InstalledAppsHelper.getAppLabel(context, packageName)
        view.findViewById<TextView>(R.id.protectedAppName).text = label

        val pinInput = view.findViewById<TextInputEditText>(R.id.pinInput)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val passwordStore = AppPasswordStore(context)

        fun tryUnlock() {
            val pin = pinInput.text?.toString().orEmpty()
            if (passwordStore.verifyPin(pin)) {
                PasswordSessionManager.unlock(packageName)
                hide(context)
            } else {
                errorText.visibility = android.view.View.VISIBLE
                errorText.text = context.getString(R.string.app_password_wrong_pin)
                pinInput.text?.clear()
            }
        }

        view.findViewById<android.view.View>(R.id.btnUnlock).setOnClickListener { tryUnlock() }
        pinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryUnlock()
                true
            } else {
                false
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
