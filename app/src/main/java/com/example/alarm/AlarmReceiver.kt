package com.example.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.VoxAlarmApplication
import com.example.data.model.RepeatType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        when (action) {
            AlarmScheduler.ACTION_TRIGGER_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                val title = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE) ?: "Alarma"
                val spokenText = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SPOKEN_TEXT) ?: title
                val repeatCount = intent.getIntExtra(AlarmScheduler.EXTRA_REPEAT_COUNT, 2)
                val soundVibration = intent.getBooleanExtra(AlarmScheduler.EXTRA_SOUND_VIBRATION, true)

                // Start Foreground Service to handle loud speaker TTS & notification
                val serviceIntent = Intent(context, AlarmSpeakerService::class.java).apply {
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_TITLE, title)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SPOKEN_TEXT, spokenText)
                    putExtra(AlarmScheduler.EXTRA_REPEAT_COUNT, repeatCount)
                    putExtra(AlarmScheduler.EXTRA_SOUND_VIBRATION, soundVibration)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }

                // Handle repeating vs one-time update in DB
                if (alarmId > 0L) {
                    val app = context.applicationContext as? VoxAlarmApplication
                    val repository = app?.repository
                    if (repository != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val alarm = repository.getAlarmById(alarmId)
                            if (alarm != null) {
                                if (alarm.repeatType == RepeatType.ONCE) {
                                    // Disable one-time alarm after firing
                                    repository.updateAlarmStatus(alarm.id, isEnabled = false)
                                } else {
                                    // Reschedule next occurrence
                                    val nextTrigger = repository.scheduler.computeNextTriggerTime(alarm)
                                    repository.updateAlarmStatus(alarm.id, isEnabled = true, nextTrigger = nextTrigger)
                                    repository.scheduler.schedule(alarm.copy(nextTriggerTimeMillis = nextTrigger))
                                }
                            }
                        }
                    }
                }
            }

            AlarmScheduler.ACTION_STOP_ALARM -> {
                AlarmSpeakerService.stopRinging(context)
            }

            AlarmScheduler.ACTION_SNOOZE_ALARM -> {
                AlarmSpeakerService.stopRinging(context)
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                val title = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE) ?: "Alarma"
                val spokenText = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SPOKEN_TEXT) ?: title
                val repeatCount = intent.getIntExtra(AlarmScheduler.EXTRA_REPEAT_COUNT, 2)

                val scheduler = AlarmScheduler(context)
                scheduler.scheduleSnooze(alarmId, title, spokenText, repeatCount, minutes = 5)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule all active alarms after reboot
                val app = context.applicationContext as? VoxAlarmApplication
                val repository = app?.repository
                if (repository != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.rescheduleAllEnabledAlarms()
                    }
                }
            }
        }
    }
}
