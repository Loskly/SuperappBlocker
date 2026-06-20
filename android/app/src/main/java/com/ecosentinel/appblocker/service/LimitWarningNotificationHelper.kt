package com.ecosentinel.appblocker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ecosentinel.appblocker.MainActivity
import com.ecosentinel.appblocker.R
import kotlin.math.abs

object LimitWarningNotificationHelper {

    private const val CHANNEL_ID = "limit_warning_channel"
    private const val NOTIFICATION_ID_BASE = 3000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_limit_warning_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_limit_warning_description)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun showAppWarning(
        context: Context,
        ruleId: String,
        appLabel: String,
        remainingMinutes: Long,
        limitMinutes: Int
    ) {
        show(
            context = context,
            notificationId = notificationIdFor(ruleId),
            title = context.getString(R.string.limit_warning_app_title, appLabel, remainingMinutes),
            text = context.getString(R.string.limit_warning_app_text, limitMinutes)
        )
    }

    fun showCategoryWarning(
        context: Context,
        ruleId: String,
        categoryName: String,
        remainingMinutes: Long,
        limitMinutes: Int
    ) {
        show(
            context = context,
            notificationId = notificationIdFor(ruleId),
            title = context.getString(R.string.limit_warning_category_title, categoryName, remainingMinutes),
            text = context.getString(R.string.limit_warning_category_text, limitMinutes)
        )
    }

    fun showGroupWarning(
        context: Context,
        ruleId: String,
        groupName: String,
        remainingMinutes: Long,
        limitMinutes: Int
    ) {
        show(
            context = context,
            notificationId = notificationIdFor(ruleId),
            title = context.getString(R.string.limit_warning_group_title, groupName, remainingMinutes),
            text = context.getString(R.string.limit_warning_group_text, limitMinutes)
        )
    }

    private fun show(context: Context, notificationId: Int, title: String, text: String) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(notificationId, notification)
    }

    private fun notificationIdFor(ruleId: String): Int {
        return NOTIFICATION_ID_BASE + abs(ruleId.hashCode() % 10_000)
    }
}
