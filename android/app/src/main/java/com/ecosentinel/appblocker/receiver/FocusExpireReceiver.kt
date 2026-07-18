package com.ecosentinel.appblocker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecosentinel.appblocker.focus.FocusExpireScheduler
import com.ecosentinel.appblocker.focus.FocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusExpireReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FOCUS_EXPIRE) {
            return
        }
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val appContext = context.applicationContext
                FocusManager(appContext).expireIfNeeded()
                FocusExpireScheduler.cancel(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FOCUS_EXPIRE = "com.ecosentinel.appblocker.FOCUS_EXPIRE"

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
