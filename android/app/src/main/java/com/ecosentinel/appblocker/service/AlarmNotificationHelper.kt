package com.ecosentinel.appblocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ecosentinel.appblocker.R
import com.ecosentinel.appblocker.ui.AlarmChallengeActivity
import kotlin.math.abs

object AlarmNotificationHelper {

    const val CHANNEL_ID = "super_alarm_channel"
    private const val NOTIFICATION_ID_BASE = 5000

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_super_alarm_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_super_alarm_description)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildRingingNotification(
        context: Context,
        alarmId: Long,
        label: String,
        isPreview: Boolean = false
    ): Notification {
        createChannel(context)
        val challengeIntent = if (isPreview) {
            AlarmChallengeActivity.intentForPreview(
                context = context,
                alarmId = alarmId,
                label = label,
                difficulty = null,
                volumePercent = 100,
                volumeGuardEnabled = true
            )
        } else {
            AlarmChallengeActivity.intent(context, alarmId)
        }
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            challengeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            (alarmId + 10_000).toInt(),
            challengeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPreview) {
            context.getString(R.string.super_alarm_preview_ringing_title)
        } else {
            context.getString(R.string.super_alarm_ringing_title)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_feature_alarm)
            .setContentTitle(title)
            .setContentText(label.ifBlank { context.getString(R.string.super_alarm_default_label) })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .build()
    }

    fun notificationId(alarmId: Long): Int = NOTIFICATION_ID_BASE + abs(alarmId.toInt() % 10_000)
}
