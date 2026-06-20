package com.ecosentinel.appblocker.alarm

import com.ecosentinel.appblocker.data.entity.SuperAlarmEntity
import java.util.Calendar
import java.util.Locale

object AlarmRepeatDays {

  const val MONDAY = 1 shl 0
  const val TUESDAY = 1 shl 1
  const val WEDNESDAY = 1 shl 2
  const val THURSDAY = 1 shl 3
  const val FRIDAY = 1 shl 4
  const val SATURDAY = 1 shl 5
  const val SUNDAY = 1 shl 6
  const val WEEKDAYS = MONDAY or TUESDAY or WEDNESDAY or THURSDAY or FRIDAY
  const val ALL_DAYS = WEEKDAYS or SATURDAY or SUNDAY

  fun maskFor(calendarDayOfWeek: Int): Int {
    return when (calendarDayOfWeek) {
      Calendar.MONDAY -> MONDAY
      Calendar.TUESDAY -> TUESDAY
      Calendar.WEDNESDAY -> WEDNESDAY
      Calendar.THURSDAY -> THURSDAY
      Calendar.FRIDAY -> FRIDAY
      Calendar.SATURDAY -> SATURDAY
      Calendar.SUNDAY -> SUNDAY
      else -> 0
    }
  }

  fun formatMask(context: android.content.Context, mask: Int): String {
    if (mask == 0) {
      return context.getString(com.ecosentinel.appblocker.R.string.super_alarm_once)
    }
    if (mask == ALL_DAYS) {
      return context.getString(com.ecosentinel.appblocker.R.string.super_alarm_every_day)
    }
    if (mask == WEEKDAYS) {
      return context.getString(com.ecosentinel.appblocker.R.string.super_alarm_weekdays)
    }
    val labels = listOf(
      MONDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_mon,
      TUESDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_tue,
      WEDNESDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_wed,
      THURSDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_thu,
      FRIDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_fri,
      SATURDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_sat,
      SUNDAY to com.ecosentinel.appblocker.R.string.super_alarm_day_sun
    )
    return labels
      .filter { (bit, _) -> mask and bit != 0 }
      .joinToString(", ") { (_, res) -> context.getString(res) }
  }

  fun formatTime(hour: Int, minute: Int): String {
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
  }

  fun computeNextTriggerMillis(
    alarm: SuperAlarmEntity,
    afterMillis: Long = System.currentTimeMillis()
  ): Long? {
    if (!alarm.enabled) {
      return null
    }
    for (dayOffset in 0..7) {
      val candidate = Calendar.getInstance().apply {
        timeInMillis = afterMillis
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, alarm.hour)
        set(Calendar.MINUTE, alarm.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
      if (candidate.timeInMillis <= afterMillis) {
        continue
      }
      if (alarm.repeatDaysMask == 0) {
        return candidate.timeInMillis
      }
      val dayBit = maskFor(candidate.get(Calendar.DAY_OF_WEEK))
      if (alarm.repeatDaysMask and dayBit != 0) {
        return candidate.timeInMillis
      }
    }
    return null
  }
}
