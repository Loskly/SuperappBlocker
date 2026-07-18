package com.ecosentinel.appblocker.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.data.entity.FocusSessionEntity
import com.ecosentinel.appblocker.ui.FocusActivity

object FocusNotificationHelper {

    const val NOTIFICATION_ID = 1002
    private const val CHANNEL_ID = "focus_timer_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_focus_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_focus_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun sync(context: Context, session: FocusSessionEntity?) {
        if (session == null) {
            cancel(context)
        } else {
            show(context, session)
        }
    }

    fun show(context: Context, session: FocusSessionEntity) {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(context, session))
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(context: Context, session: FocusSessionEntity): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, FocusActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val modeText = when (session.mode) {
            FocusMode.STRICT -> context.getString(R.string.focus_notification_strict)
            FocusMode.SOFT -> context.getString(R.string.focus_notification_soft)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.focus_notification_title))
            .setContentText(modeText)
            .setSubText(context.getString(R.string.focus_notification_remaining))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(session.expiresAtMillis)
            .setShowWhen(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .build()
    }
}
