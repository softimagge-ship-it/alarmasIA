package com.example.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.AlarmEntity
import com.example.data.model.RepeatType
import java.util.Calendar
import java.util.TimeZone

object CalendarHelper {
    private const val TAG = "CalendarHelper"

    fun hasCalendarPermission(context: Context): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inserts an event directly into the device's primary calendar.
     * Returns the generated event ID, or null if failed/permission denied.
     */
    fun insertAlarmEvent(context: Context, alarm: AlarmEntity): Long? {
        if (!hasCalendarPermission(context)) {
            Log.w(TAG, "Calendar permission not granted")
            return null
        }

        try {
            val primaryCalendarId = getPrimaryCalendarId(context) ?: 1L

            val startMillis = calculateEventStartTime(alarm)
            val endMillis = startMillis + (30 * 60 * 1000) // 30 minutes duration

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, "📢 " + alarm.title)
                put(CalendarContract.Events.DESCRIPTION, "Aviso programado con VoxAlarm:\nLocución por altavoz: \"${alarm.spokenMessage}\"")
                put(CalendarContract.Events.CALENDAR_ID, primaryCalendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

                // Set recurrence rule if applicable
                when (alarm.repeatType) {
                    RepeatType.DAILY -> put(CalendarContract.Events.RRULE, "FREQ=DAILY")
                    RepeatType.WEEKDAYS -> put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR")
                    RepeatType.WEEKENDS -> put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=SA,SU")
                    RepeatType.CUSTOM -> {
                        val byDays = formatRruleDays(alarm.repeatDays)
                        if (byDays.isNotEmpty()) {
                            put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=$byDays")
                        }
                    }
                    RepeatType.ONCE -> { /* No RRULE */ }
                }
            }

            val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()

            // Add reminder notification to calendar event (0 min / at event time)
            if (eventId != null) {
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    put(CalendarContract.Reminders.MINUTES, 0)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }

            return eventId
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting event into calendar: ${e.message}", e)
            return null
        }
    }

    fun deleteAlarmEvent(context: Context, eventId: Long) {
        if (!hasCalendarPermission(context)) return
        try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event from calendar: ${e.message}")
        }
    }

    fun createCalendarViewIntent(eventId: Long): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return Intent(Intent.ACTION_VIEW).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createOpenCalendarIntent(timeMillis: Long): Intent {
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, timeMillis)
        return Intent(Intent.ACTION_VIEW).apply {
            data = builder.build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE
        )
        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idCol = it.getColumnIndex(CalendarContract.Calendars._ID)
                    return it.getLong(idCol)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query primary calendar ID: ${e.message}")
        }
        return null
    }

    private fun calculateEventStartTime(alarm: AlarmEntity): Long {
        val cal = Calendar.getInstance()
        if (alarm.dateMillis != null && alarm.repeatType == RepeatType.ONCE) {
            val targetCal = Calendar.getInstance().apply { timeInMillis = alarm.dateMillis }
            cal.set(Calendar.YEAR, targetCal.get(Calendar.YEAR))
            cal.set(Calendar.MONTH, targetCal.get(Calendar.MONTH))
            cal.set(Calendar.DAY_OF_MONTH, targetCal.get(Calendar.DAY_OF_MONTH))
        }
        cal.set(Calendar.HOUR_OF_DAY, alarm.hour)
        cal.set(Calendar.MINUTE, alarm.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // If in the past today for a one-time reminder, push to tomorrow
        if (cal.timeInMillis <= System.currentTimeMillis() && alarm.repeatType != RepeatType.ONCE) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun formatRruleDays(repeatDays: String): String {
        val mapping = mapOf(
            "1" to "MO", "2" to "TU", "3" to "WE",
            "4" to "TH", "5" to "FR", "6" to "SA", "7" to "SU"
        )
        return repeatDays.split(",")
            .mapNotNull { mapping[it.trim()] }
            .joinToString(",")
    }
}
