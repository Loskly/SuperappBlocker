package com.ecosentinel.appblocker.data

import androidx.room.TypeConverter
import com.ecosentinel.appblocker.engine.BlockMode
import com.ecosentinel.appblocker.engine.BlockModuleType
import com.ecosentinel.appblocker.engine.RuleLockMode
import com.ecosentinel.appblocker.engine.TargetType
import com.ecosentinel.appblocker.alarm.AlarmChallengeDifficulty
import com.ecosentinel.appblocker.focus.FocusMode

class Converters {
    @TypeConverter
    fun fromBlockModuleType(value: BlockModuleType): String = value.name

    @TypeConverter
    fun toBlockModuleType(value: String): BlockModuleType = BlockModuleType.valueOf(value)

    @TypeConverter
    fun fromTargetType(value: TargetType): String = value.name

    @TypeConverter
    fun toTargetType(value: String): TargetType = TargetType.valueOf(value)

    @TypeConverter
    fun fromBlockMode(value: BlockMode): String = value.name

    @TypeConverter
    fun toBlockMode(value: String): BlockMode = BlockMode.valueOf(value)

    @TypeConverter
    fun fromRuleLockMode(value: RuleLockMode): String = value.name

    @TypeConverter
    fun toRuleLockMode(value: String): RuleLockMode = RuleLockMode.valueOf(value)

    @TypeConverter
    fun fromFocusMode(value: FocusMode): String = value.name

    @TypeConverter
    fun toFocusMode(value: String): FocusMode = FocusMode.valueOf(value)

    @TypeConverter
    fun fromAlarmChallengeDifficulty(value: AlarmChallengeDifficulty): String = value.name

    @TypeConverter
    fun toAlarmChallengeDifficulty(value: String): AlarmChallengeDifficulty =
        AlarmChallengeDifficulty.valueOf(value)
}
