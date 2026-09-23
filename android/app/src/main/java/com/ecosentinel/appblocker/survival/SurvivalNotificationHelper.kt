package com.ecosentinel.appblocker.survival

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.ui.PermissionsActivity

object SurvivalNotificationHelper {

    const val WARNING_NOTIFICATION_ID = 6001
    private const val CHANNEL_ID = "survival_mode_channel"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_survival_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_survival_description)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun showWarning(context: Context, issues: List<AppHealthIssue>) {
        if (issues.isEmpty()) {
            cancelWarning(context)
            return
        }
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val pendingIntent = PendingIntent.getActivity(
            context,
            WARNING_NOTIFICATION_ID,
            Intent(context, PermissionsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val issueText = issues
            .take(4)
            .joinToString(" • ") { context.getString(it.labelRes) }
            .ifBlank { context.getString(R.string.survival_notification_text) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(context.getString(R.string.survival_notification_title))
            .setContentText(issueText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(issueText))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(WARNING_NOTIFICATION_ID, notification)
    }

    fun cancelWarning(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(WARNING_NOTIFICATION_ID)
    }
}
