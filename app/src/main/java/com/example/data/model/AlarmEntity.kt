package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

enum class AlarmType {
    ALARM,       // Alarma sonora / matutina
    REMINDER,    // Aviso / Recordatorio puntual
    EVENT        // Evento sincronizado de calendario
}

enum class RepeatType {
    ONCE,        // Sin repetición / una sola vez
    DAILY,       // Todos los días
    WEEKDAYS,    // Lunes a Viernes
    WEEKENDS,    // Sábado y Domingo
    CUSTOM       // Días seleccionados (1=Lunes .. 7=Domingo)
}

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: AlarmType = AlarmType.ALARM,
    val hour: Int,
    val minute: Int,
    val dateMillis: Long? = null,              // Specific target date in epoch ms (for ONCE)
    val repeatType: RepeatType = RepeatType.ONCE,
    val repeatDays: String = "",               // Comma-separated list e.g. "1,2,3,4,5" (1=Mon..7=Sun)
    val spokenMessage: String,                 // Custom text to be spoken via speaker
    val isEnabled: Boolean = true,
    val syncToCalendar: Boolean = false,
    val calendarEventId: Long? = null,
    val soundVibration: Boolean = true,
    val repeatSpeechCount: Int = 2,            // Speak 1, 2, 3, 4, or 5 times
    val createdAt: Long = System.currentTimeMillis(),
    val nextTriggerTimeMillis: Long = 0L
) {
    fun getFormattedTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun getRepeatDescription(): String {
        return when (repeatType) {
            RepeatType.ONCE -> {
                if (dateMillis != null) {
                    val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    val month = cal.get(Calendar.MONTH) + 1
                    val year = cal.get(Calendar.YEAR)
                    "Fecha: %02d/%02d/%d".format(day, month, year)
                } else {
                    "Una sola vez"
                }
            }
            RepeatType.DAILY -> "Todos los días"
            RepeatType.WEEKDAYS -> "Lunes a Viernes"
            RepeatType.WEEKENDS -> "Fines de semana"
            RepeatType.CUSTOM -> {
                val days = repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }
                if (days.isEmpty()) "Sin repetición"
                else {
                    val dayNames = mapOf(
                        1 to "Lun", 2 to "Mar", 3 to "Mié",
                        4 to "Jue", 5 to "Vie", 6 to "Sáb", 7 to "Dom"
                    )
                    days.mapNotNull { dayNames[it] }.joinToString(", ")
                }
            }
        }
    }
}
