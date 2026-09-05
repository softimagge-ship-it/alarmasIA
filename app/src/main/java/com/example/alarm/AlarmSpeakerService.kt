package com.example.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.VoxAlarmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveRingingState(
    val alarmId: Long = 0L,
    val title: String = "",
    val spokenText: String = "",
    val isRinging: Boolean = false,
    val startedAt: Long = 0L
)

class AlarmSpeakerService : Service() {

    companion object {
        private const val TAG = "AlarmSpeakerService"
        const val NOTIFICATION_ID = 4001

        private val _ringingState = MutableStateFlow(ActiveRingingState())
        val ringingState = _ringingState.asStateFlow()

        fun stopRinging(context: Context) {
            val intent = Intent(context, AlarmSpeakerService::class.java).apply {
                action = AlarmScheduler.ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "voxalarm:speaker_wakelock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes timeout

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == AlarmScheduler.ACTION_STOP_ALARM) {
            stopAlarm()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L) ?: 0L
        val title = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE) ?: "Alarma VoxAlarm"
        val spokenText = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_SPOKEN_TEXT) ?: title
        val repeatCount = intent?.getIntExtra(AlarmScheduler.EXTRA_REPEAT_COUNT, 2) ?: 2
        val soundVibration = intent?.getBooleanExtra(AlarmScheduler.EXTRA_SOUND_VIBRATION, true) ?: true

        _ringingState.value = ActiveRingingState(
            alarmId = alarmId,
            title = title,
            spokenText = spokenText,
            isRinging = true,
            startedAt = System.currentTimeMillis()
        )

        val notification = buildForegroundNotification(alarmId, title, spokenText)
        startForeground(NOTIFICATION_ID, notification)

        triggerAlarmAlerts(alarmId, title, spokenText, repeatCount, soundVibration)

        return START_NOT_STICKY
    }

    private fun triggerAlarmAlerts(
        alarmId: Long,
        title: String,
        spokenText: String,
        repeatCount: Int,
        soundVibration: Boolean
    ) {
        if (soundVibration) {
            // Play gentle ringtone chime and vibrate
            try {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.w(TAG, "Ringtone error: ${e.message}")
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pattern = longArrayOf(0, 400, 200, 400, 200, 600)
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 400, 200, 400, 200, 600), -1)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vibration error: ${e.message}")
            }
        }

        // Delay 1 second for the alert chime, then speak the configured announcement clearly via speaker
        serviceScope.launch {
            delay(1200)
            try {
                ringtone?.stop()
            } catch (_: Exception) {}

            val tts = TTSManager.getInstance(applicationContext)
            tts.speakAloud(
                text = spokenText,
                repeatCount = repeatCount,
                ensureSpeaker = true
            )
        }
    }

    private fun buildForegroundNotification(
        alarmId: Long,
        title: String,
        spokenText: String
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_RINGING", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_STOP_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE_ALARM
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_TITLE, title)
            putExtra(AlarmScheduler.EXTRA_ALARM_SPOKEN_TEXT, spokenText)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            103,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VoxAlarmApplication.CHANNEL_ID_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("📢 $title")
            .setContentText("Locutando por altavoz: \"$spokenText\"")
            .setStyle(NotificationCompat.BigTextStyle().bigText("📢 Locución por altavoz:\n\"$spokenText\""))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, "Posponer 5 min", snoozePendingIntent)
            .build()
    }

    private fun stopAlarm() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
            TTSManager.getInstance(applicationContext).stop()
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stopAlarm: ${e.message}")
        }
        _ringingState.value = ActiveRingingState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
