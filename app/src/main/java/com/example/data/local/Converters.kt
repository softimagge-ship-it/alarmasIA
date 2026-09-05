package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.AlarmType
import com.example.data.model.RepeatType

class Converters {
    @TypeConverter
    fun fromAlarmType(value: AlarmType): String = value.name

    @TypeConverter
    fun toAlarmType(value: String): AlarmType = try {
        AlarmType.valueOf(value)
    } catch (e: Exception) {
        AlarmType.ALARM
    }

    @TypeConverter
    fun fromRepeatType(value: RepeatType): String = value.name

    @TypeConverter
    fun toRepeatType(value: String): RepeatType = try {
        RepeatType.valueOf(value)
    } catch (e: Exception) {
        RepeatType.ONCE
    }
}
