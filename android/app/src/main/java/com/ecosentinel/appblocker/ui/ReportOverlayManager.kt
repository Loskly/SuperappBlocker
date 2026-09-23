package com.ecosentinel.appblocker.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.AppDatabase
import com.ecosentinel.appblocker.data.entity.ReportEntity
import com.ecosentinel.appblocker.data.repository.ReportSettings
import com.ecosentinel.appblocker.service.ReportScheduler
import com.ecosentinel.appblocker.util.PermissionHelper
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReportOverlayManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    @Volatile
    private var overlayView: View? = null

    fun show(context: Context) {
        if (!PermissionHelper.canDrawOverlays(context)) {
            return
        }

        val appContext = context.applicationContext
        if (overlayView != null) {
            return
        }

        val themedContext = ContextThemeWrapper(appContext, R.style.Theme_Appbllocker_BlockOverlay)
        val inflater = LayoutInflater.from(themedContext)
        val view = inflater.inflate(R.layout.overlay_report, null)

        setupView(appContext, view)

        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        @Suppress("DEPRECATION")
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
        } catch (_: Exception) {
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
        }
    }

    private fun setupView(context: Context, view: View) {
        val editText = view.findViewById<EditText>(R.id.reportEditText)
        val charCountText = view.findViewById<TextView>(R.id.charCountText)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitReport)
        val btnSnooze = view.findViewById<MaterialButton>(R.id.btnSnoozeReport)

        val requiredChars = ReportSettings.getCharCount(context)
        val maxSnoozes = ReportSettings.getMaxSnoozes(context)
        val currentSnoozes = ReportSettings.getCurrentSnoozes(context)

        charCountText.text = "0 / $requiredChars"

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                charCountText.text = "$len / $requiredChars"
                // Change color if enough
                if (len >= requiredChars) {
                    charCountText.setTextColor(android.graphics.Color.GREEN) // Or theme color
                    btnSubmit.isEnabled = true
                } else {
                    charCountText.setTextColor(android.graphics.Color.RED)
                    btnSubmit.isEnabled = false
                }
            }
        })

        if (currentSnoozes >= maxSnoozes) {
            btnSnooze.isEnabled = false
            btnSnooze.text = "Отложить (лимит исчерпан)"
        } else {
            val remaining = maxSnoozes - currentSnoozes
            btnSnooze.text = "Отложить (осталось $remaining)"
            btnSnooze.setOnClickListener {
                ReportSettings.incrementCurrentSnoozes(context)
                ReportScheduler.scheduleSnooze(context)
                hideInternal(context)
            }
        }

        btnSubmit.setOnClickListener {
            val text = editText.text.toString()
            btnSubmit.isEnabled = false
            
            scope.launch {
                val db = AppDatabase.getInstance(context)
                db.reportDao().insert(
                    ReportEntity(text = text, timestamp = System.currentTimeMillis())
                )
                ReportSettings.resetCurrentSnoozes(context)
                ReportScheduler.scheduleNext(context)
                hideInternal(context)
            }
        }
    }
}
