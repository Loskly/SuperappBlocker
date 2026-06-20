package com.ecosentinel.appblocker.engine

import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class BlockSchedule(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    fun toJson(): String {
        return JSONObject()
            .put("startHour", startHour)
            .put("startMinute", startMinute)
            .put("endHour", endHour)
            .put("endMinute", endMinute)
            .toString()
    }

    fun formatRange(): String {
        return "${formatTime(startHour, startMinute)} – ${formatTime(endHour, endMinute)}"
    }

    fun isValid(): Boolean {
        val start = toMinutesOfDay(startHour, startMinute)
        val end = toMinutesOfDay(endHour, endMinute)
        return start != end
    }

    fun isActiveNow(now: Calendar = Calendar.getInstance()): Boolean {
        val nowMinutes = toMinutesOfDay(
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )
        val startMinutes = toMinutesOfDay(startHour, startMinute)
        val endMinutes = toMinutesOfDay(endHour, endMinute)

        return if (startMinutes < endMinutes) {
            nowMinutes in startMinutes until endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    fun millisUntilBlockEnds(now: Calendar = Calendar.getInstance()): Long {
        val endCalendar = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
        }

        val nowMinutes = toMinutesOfDay(
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )
        val startMinutes = toMinutesOfDay(startHour, startMinute)
        val endMinutes = toMinutesOfDay(endHour, endMinute)

        if (startMinutes < endMinutes) {
            if (endCalendar.timeInMillis <= now.timeInMillis) {
                return 0L
            }
        } else if (nowMinutes >= startMinutes) {
            endCalendar.add(Calendar.DAY_OF_YEAR, 1)
        } else if (endCalendar.timeInMillis <= now.timeInMillis) {
            endCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return (endCalendar.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    companion object {
        fun fromJson(json: String?): BlockSchedule? {
            if (json.isNullOrBlank()) return null
            return try {
                val objectJson = JSONObject(json)
                BlockSchedule(
                    startHour = objectJson.getInt("startHour"),
                    startMinute = objectJson.getInt("startMinute"),
                    endHour = objectJson.getInt("endHour"),
                    endMinute = objectJson.getInt("endMinute")
                )
            } catch (_: Exception) {
                null
            }
        }

        fun formatTime(hour: Int, minute: Int): String {
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        }

        fun formatDurationUntil(millis: Long): String {
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return when {
                hours > 0 -> "${hours}ч ${minutes}мин"
                minutes > 0 -> "${minutes}мин"
                else -> "< 1мин"
            }
        }

        private fun toMinutesOfDay(hour: Int, minute: Int): Int = hour * 60 + minute
    }
}
