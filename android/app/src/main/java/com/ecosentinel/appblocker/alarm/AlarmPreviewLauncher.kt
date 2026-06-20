package com.ecosentinel.appblocker.alarm

import android.content.Context
import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import com.ecosentinel.appblocker.service.AlarmRingingService

object AlarmPreviewLauncher {

    const val PREVIEW_ALARM_ID = -1L

    fun start(context: Context, alarm: SuperAlarmEntity) {
        val previewId = if (alarm.id > 0L) alarm.id else PREVIEW_ALARM_ID
        AlarmRingingService.startPreview(context, previewId, alarm)
    }
}
