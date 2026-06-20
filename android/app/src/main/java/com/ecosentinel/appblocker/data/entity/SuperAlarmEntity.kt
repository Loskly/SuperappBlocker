package com.ecosentinel.appblocker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty

@Entity(tableName = "super_alarms")
data class SuperAlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bitmask: bit 0 = Mon … bit 6 = Sun. 0 = one-time alarm. */
    val repeatDaysMask: Int,
    val label: String,
    val enabled: Boolean,
    val challengeDifficulty: AlarmChallengeDifficulty,
    val volumePercent: Int = 100,
    val volumeGuardEnabled: Boolean = true,
    /** Empty = system default alarm sound. */
    val soundUri: String = ""
)
