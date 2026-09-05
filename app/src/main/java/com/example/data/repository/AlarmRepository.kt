package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.alarm.AlarmScheduler
import com.example.calendar.CalendarHelper
import com.example.data.local.AlarmDao
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmType
import com.example.data.model.RepeatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val context: Context
) {
    val scheduler = AlarmScheduler(context)
    val allAlarms: Flow<List<AlarmEntity>> = alarmDao.getAllAlarms()
    val enabledAlarms: Flow<List<AlarmEntity>> = alarmDao.getEnabledAlarms()

    private val prefs = context.getSharedPreferences("voxalarm_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "AlarmRepository"
        private const val KEY_HAS_SEEDED = "has_seeded_sample_alarms"
        private const val KEY_APP_THEME = "app_theme_preset"
    }

    fun getSavedThemeId(): String {
        return prefs.getString(KEY_APP_THEME, "indigo") ?: "indigo"
    }

    fun saveThemeId(id: String) {
        prefs.edit().putString(KEY_APP_THEME, id).apply()
    }

    suspend fun getAlarmById(id: Long): AlarmEntity? = withContext(Dispatchers.IO) {
        alarmDao.getAlarmById(id)
    }

    suspend fun insertAlarm(alarm: AlarmEntity): Long = withContext(Dispatchers.IO) {
        val nextTrigger = scheduler.computeNextTriggerTime(alarm)
        var toInsert = alarm.copy(nextTriggerTimeMillis = nextTrigger)

        // Sincronizar con Calendario si está activado
        if (toInsert.syncToCalendar) {
            val eventId = CalendarHelper.insertAlarmEvent(context, toInsert)
            toInsert = toInsert.copy(calendarEventId = eventId)
        }

        val id = alarmDao.insertAlarm(toInsert)
        val finalAlarm = toInsert.copy(id = id)

        if (finalAlarm.isEnabled) {
            scheduler.schedule(finalAlarm)
        }
        id
    }

    suspend fun updateAlarm(alarm: AlarmEntity) = withContext(Dispatchers.IO) {
        val nextTrigger = scheduler.computeNextTriggerTime(alarm)
        var updated = alarm.copy(nextTriggerTimeMillis = nextTrigger)

        // Update Calendar Event
        if (updated.syncToCalendar) {
            if (updated.calendarEventId != null) {
                CalendarHelper.deleteAlarmEvent(context, updated.calendarEventId!!)
            }
            val eventId = CalendarHelper.insertAlarmEvent(context, updated)
            updated = updated.copy(calendarEventId = eventId)
        } else if (updated.calendarEventId != null) {
            CalendarHelper.deleteAlarmEvent(context, updated.calendarEventId!!)
            updated = updated.copy(calendarEventId = null)
        }

        alarmDao.updateAlarm(updated)

        if (updated.isEnabled) {
            scheduler.schedule(updated)
        } else {
            scheduler.cancel(updated.id)
        }
    }

    suspend fun toggleAlarm(alarm: AlarmEntity, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val nextTrigger = if (isEnabled) scheduler.computeNextTriggerTime(alarm) else 0L
        alarmDao.updateAlarmStatus(alarm.id, isEnabled, nextTrigger)
        if (isEnabled) {
            scheduler.schedule(alarm.copy(isEnabled = true, nextTriggerTimeMillis = nextTrigger))
        } else {
            scheduler.cancel(alarm.id)
        }
    }

    suspend fun updateAlarmStatus(id: Long, isEnabled: Boolean, nextTrigger: Long = 0L) = withContext(Dispatchers.IO) {
        alarmDao.updateAlarmStatus(id, isEnabled, nextTrigger)
    }

    suspend fun deleteAlarm(alarm: AlarmEntity) = withContext(Dispatchers.IO) {
        scheduler.cancel(alarm.id)
        if (alarm.calendarEventId != null) {
            CalendarHelper.deleteAlarmEvent(context, alarm.calendarEventId)
        }
        alarmDao.deleteAlarm(alarm)
    }

    suspend fun rescheduleAllEnabledAlarms() = withContext(Dispatchers.IO) {
        val enabledList = alarmDao.getEnabledAlarmsList()
        for (alarm in enabledList) {
            val nextTrigger = scheduler.computeNextTriggerTime(alarm)
            alarmDao.updateAlarmStatus(alarm.id, isEnabled = true, nextTrigger = nextTrigger)
            scheduler.schedule(alarm.copy(nextTriggerTimeMillis = nextTrigger))
        }
        Log.d(TAG, "Rescheduled ${enabledList.size} alarms")
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        val hasSeeded = prefs.getBoolean(KEY_HAS_SEEDED, false)
        if (hasSeeded) {
            return@withContext
        }

        val allAlarmsList = alarmDao.getEnabledAlarmsList()
        if (allAlarmsList.isEmpty()) {
            val cal = Calendar.getInstance()
            val sampleAlarms = listOf(
                AlarmEntity(
                    title = "Despertar y Motivación",
                    type = AlarmType.ALARM,
                    hour = 7,
                    minute = 30,
                    repeatType = RepeatType.WEEKDAYS,
                    spokenMessage = "¡Buenos días! Es hora de levantarse, son las siete y media. Recuerda beber un vaso de agua.",
                    isEnabled = true,
                    syncToCalendar = true,
                    repeatSpeechCount = 2
                ),
                AlarmEntity(
                    title = "Tomar Medicación / Vitaminas",
                    type = AlarmType.REMINDER,
                    hour = 9,
                    minute = 0,
                    repeatType = RepeatType.DAILY,
                    spokenMessage = "Atención: Es hora de tomar la medicación y las vitaminas con el desayuno.",
                    isEnabled = true,
                    syncToCalendar = false,
                    repeatSpeechCount = 2
                ),
                AlarmEntity(
                    title = "Reunión de Planificación",
                    type = AlarmType.EVENT,
                    hour = 17,
                    minute = 0,
                    repeatType = RepeatType.ONCE,
                    dateMillis = cal.timeInMillis + (24 * 3600 * 1000L),
                    spokenMessage = "Aviso importante: Tienes la reunión de planificación en 15 minutos en la sala principal.",
                    isEnabled = true,
                    syncToCalendar = true,
                    repeatSpeechCount = 2
                )
            )

            sampleAlarms.forEach { insertAlarm(it) }
        }

        // Mark as seeded so even if user deletes all alarms, they will never be re-added automatically
        prefs.edit().putBoolean(KEY_HAS_SEEDED, true).apply()
    }
}
