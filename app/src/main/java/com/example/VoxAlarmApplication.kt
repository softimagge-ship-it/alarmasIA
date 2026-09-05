package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import com.example.alarm.TTSManager
import com.example.data.local.AppDatabase
import com.example.data.repository.AlarmRepository

class VoxAlarmApplication : Application() {

    companion object {
        const val CHANNEL_ID_ALARM = "vox_alarm_channel_high_v2"
        const val CHANNEL_ID_TIMER = "vox_timer_channel_high_v2"
        lateinit var instance: VoxAlarmApplication
            private set
    }

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { AlarmRepository(database.alarmDao(), this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        // Pre-warm TTS engine in background
        TTSManager.getInstance(this).init()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                "Avisos y Alarmas con Locución",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alarmas y recordatorios hablados por altavoz"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val timerChannel = NotificationChannel(
                CHANNEL_ID_TIMER,
                "Temporizador y Cuenta Regresiva",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones al finalizar temporizadores"
                enableVibration(true)
                setSound(Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(timerChannel)
        }
    }
}
