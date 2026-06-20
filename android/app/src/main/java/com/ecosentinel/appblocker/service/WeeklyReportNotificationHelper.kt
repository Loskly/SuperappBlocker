package com.ecosentinel.appblocker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.tracker.UsageTracker
import com.ecosentinel.appblocker.tracker.WeeklyUsageSummary
import com.ecosentinel.appblocker.ui.WeeklyReportActivity
import kotlin.math.abs

object WeeklyReportNotificationHelper {

    private const val CHANNEL_ID = "weekly_report_channel"
    private const val NOTIFICATION_ID = 4001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_weekly_report_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_weekly_report_description)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, report: WeeklyUsageSummary) {
        createChannel(context)
        val tracker = UsageTracker(context)
        val totalText = tracker.formatDuration(report.totalMillis)
        val averageText = tracker.formatDuration(report.averageDailyMillis)
        val title = context.getString(R.string.weekly_report_notification_title)
        val summary = context.getString(
            R.string.weekly_report_notification_text,
            totalText,
            averageText
        )

        val topApp = tracker.toWeeklyAppDetails(report.byPackage, report.dayCount).firstOrNull()
        val bigText = buildString {
            append(summary)
            if (topApp != null) {
                append("\n")
                append(
                    context.getString(
                        R.string.weekly_report_notification_top_app,
                        topApp.label,
                        tracker.formatDuration(topApp.totalMillis)
                    )
                )
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            WeeklyReportActivity.createIntent(context, report.weekStartDateKey),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID + abs(report.weekStartDateKey.hashCode() % 100), notification)
    }
}
