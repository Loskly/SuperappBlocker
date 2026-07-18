package com.ecosentinel.appblocker.tracker

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar dates for usage stats use the device timezone.
 * [MaterialDatePicker](https://github.com/material-components/material-components-android)
 * encodes selection as UTC midnight for the chosen Y-M-D — convert only at the UI boundary.
 */
object DateKeys {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val pickerZone: ZoneOffset = ZoneOffset.UTC

    private val dayLabelFormat = DateTimeFormatter.ofPattern("EEE", Locale("ru"))
    private val displayDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM", Locale("ru"))

    fun today(): String = LocalDate.now(zone).toString()

    fun offset(daysAgo: Int): String = LocalDate.now(zone).minusDays(daysAgo.toLong()).toString()

    fun minSelectable(): String = LocalDate.now(zone).minusMonths(1).toString()

    fun parse(dateKey: String): LocalDate? = runCatching { LocalDate.parse(dateKey) }.getOrNull()

    fun startOfDayMillis(dateKey: String): Long? =
        parse(dateKey)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()

    fun endOfDayMillis(dateKey: String): Long? =
        parse(dateKey)?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()?.minus(1)

    fun dateKeysBetween(startDateKey: String, endDateKey: String): List<String> {
        val start = parse(startDateKey) ?: return emptyList()
        val end = parse(endDateKey) ?: return emptyList()
        if (end.isBefore(start)) {
            return emptyList()
        }
        val keys = mutableListOf<String>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            keys += cursor.toString()
            cursor = cursor.plusDays(1)
        }
        return keys
    }

    fun weekStart(dateKey: String): String {
        val date = parse(dateKey) ?: return dateKey
        return date.minusDays((date.dayOfWeek.value - 1).toLong()).toString()
    }

    fun plusDays(dateKey: String, days: Long): String {
        val date = parse(dateKey) ?: return dateKey
        return date.plusDays(days).toString()
    }

    fun minusDays(dateKey: String, days: Long): String = plusDays(dateKey, -days)

    fun clamp(dateKey: String, minKey: String, maxKey: String): String = when {
        dateKey < minKey -> minKey
        dateKey > maxKey -> maxKey
        else -> dateKey
    }

    fun toPickerUtcMillis(dateKey: String): Long? =
        parse(dateKey)?.atStartOfDay(pickerZone)?.toInstant()?.toEpochMilli()

    fun fromPickerUtcMillis(utcMillis: Long): String =
        Instant.ofEpochMilli(utcMillis).atZone(pickerZone).toLocalDate().toString()

    fun resolveFromPicker(utcMillis: Long): String =
        clamp(fromPickerUtcMillis(utcMillis), minSelectable(), today())

    fun dayLabel(dateKey: String): String {
        val date = parse(dateKey) ?: return dateKey
        return date.format(dayLabelFormat)
    }

    fun formatDisplay(dateKey: String): String {
        val date = parse(dateKey) ?: return dateKey
        return date.format(displayDateFormat)
    }

    fun formatWeekRange(startDateKey: String, endDateKey: String): String {
        val start = parse(startDateKey)
        val end = parse(endDateKey)
        if (start == null || end == null) {
            return "$startDateKey – $endDateKey"
        }
        val startMonth = start.format(monthFormat)
        val endMonth = end.format(monthFormat)
        return if (startMonth == endMonth) {
            "${start.dayOfMonth}–${end.dayOfMonth} $endMonth ${end.year}"
        } else {
            "${start.dayOfMonth} $startMonth – ${end.dayOfMonth} $endMonth ${end.year}"
        }
    }

    fun millisUntilMidnight(): Long {
        val now = Instant.now()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
        return midnight.toEpochMilli() - now.toEpochMilli()
    }
}
