package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.model.AlarmEntity
import com.example.data.model.RepeatType
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_TITLE = "extra_alarm_title"
        const val EXTRA_ALARM_SPOKEN_TEXT = "extra_alarm_spoken_text"
        const val EXTRA_REPEAT_COUNT = "extra_repeat_count"
        const val EXTRA_SOUND_VIBRATION = "extra_sound_vibration"
        const val ACTION_TRIGGER_ALARM = "com.example.ACTION_TRIGGER_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.ACTION_SNOOZE_ALARM"
    }

    fun schedule(alarm: AlarmEntity): Long {
        if (!alarm.isEnabled) {
            cancel(alarm.id)
            return 0L
        }

        val triggerTime = computeNextTriggerTime(alarm)
        if (triggerTime <= 0L) {
            Log.w(TAG, "Trigger time is invalid for alarm ${alarm.id}")
            return 0L
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_TITLE, alarm.title)
            putExtra(EXTRA_ALARM_SPOKEN_TEXT, alarm.spokenMessage)
            putExtra(EXTRA_REPEAT_COUNT, alarm.repeatSpeechCount)
            putExtra(EXTRA_SOUND_VIBRATION, alarm.soundVibration)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled alarm ${alarm.id} for $triggerTime (${Calendar.getInstance().apply { timeInMillis = triggerTime }.time})")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm: ${e.message}")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }

        return triggerTime
    }

    fun cancel(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm $alarmId")
        }
    }

    fun scheduleSnooze(alarmId: Long, title: String, spokenText: String, repeatCount: Int, minutes: Int = 5) {
        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, "$title (Pospuesta)")
            putExtra(EXTRA_ALARM_SPOKEN_TEXT, spokenText)
            putExtra(EXTRA_REPEAT_COUNT, repeatCount)
            putExtra(EXTRA_SOUND_VIBRATION, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + 999900).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    fun computeNextTriggerTime(alarm: AlarmEntity): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when (alarm.repeatType) {
            RepeatType.ONCE -> {
                if (alarm.dateMillis != null) {
                    val dateCal = Calendar.getInstance().apply { timeInMillis = alarm.dateMillis }
                    target.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                    target.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                    target.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                } else if (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            RepeatType.DAILY -> {
                if (target.before(now)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            RepeatType.WEEKDAYS -> {
                while (target.before(now) || isWeekend(target)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            RepeatType.WEEKENDS -> {
                while (target.before(now) || !isWeekend(target)) {
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }

            RepeatType.CUSTOM -> {
                val targetDays = alarm.repeatDays.split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .toSet()

                if (targetDays.isEmpty()) {
                    if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
                    return target.timeInMillis
                }

                // Check up to 8 days ahead
                for (i in 0..7) {
                    val currentDayOfWeek = calendarDayToIsoDay(target.get(Calendar.DAY_OF_WEEK))
                    if (targetDays.contains(currentDayOfWeek) && target.after(now)) {
                        return target.timeInMillis
                    }
                    target.add(Calendar.DAY_OF_YEAR, 1)
                }
                return target.timeInMillis
            }
        }
    }

    private fun isWeekend(cal: Calendar): Boolean {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY
    }

    private fun calendarDayToIsoDay(calDay: Int): Int {
        return when (calDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }
}
