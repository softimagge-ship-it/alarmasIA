package com.example.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.VoxAlarmApplication
import com.example.ai.GeminiAlarmParser
import com.example.ai.ParsedAlarmResult
import com.example.alarm.AlarmScheduler
import com.example.alarm.AlarmSpeakerService
import com.example.alarm.TTSManager
import com.example.calendar.CalendarHelper
import com.example.data.model.ActiveTimerState
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmType
import com.example.data.model.RepeatType
import com.example.data.model.TimerPreset
import com.example.data.repository.AlarmRepository
import com.example.speech.VoiceRecognizerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class AlarmFilter {
    ALL,
    ALARMS,
    REMINDERS,
    CALENDAR
}

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AlarmRepository = (application as VoxAlarmApplication).repository
    val ttsManager: TTSManager = TTSManager.getInstance(application)
    val voiceHelper: VoiceRecognizerHelper = VoiceRecognizerHelper(application)

    // Current time ticker
    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis = _currentTimeMillis.asStateFlow()

    // Alarm list and filtering
    val allAlarms: StateFlow<List<AlarmEntity>> = repository.allAlarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(AlarmFilter.ALL)
    val filter = _filter.asStateFlow()

    val filteredAlarms: StateFlow<List<AlarmEntity>> = combine(allAlarms, filter) { alarms, currentFilter ->
        when (currentFilter) {
            AlarmFilter.ALL -> alarms
            AlarmFilter.ALARMS -> alarms.filter { it.type == AlarmType.ALARM }
            AlarmFilter.REMINDERS -> alarms.filter { it.type == AlarmType.REMINDER }
            AlarmFilter.CALENDAR -> alarms.filter { it.syncToCalendar || it.type == AlarmType.EVENT }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Ringing Alarm State
    val ringingState = AlarmSpeakerService.ringingState

    // Timer State
    private val _timerState = MutableStateFlow(ActiveTimerState())
    val timerState = _timerState.asStateFlow()
    private var timerJob: Job? = null

    val timerPresets = listOf(
        TimerPreset("pomodoro", "Pomodoro", 25 * 60, "¡Tiempo de estudio Pomodoro finalizado! Tómate un descanso de 5 minutos.", "school"),
        TimerPreset("tea", "Infusión / Té", 3 * 60, "¡Tu té o infusión está listo para tomar!", "emoji_food_beverage"),
        TimerPreset("cooking", "Cocina / Pasta", 10 * 60, "¡La cocción ha terminado! Revisa la comida.", "restaurant"),
        TimerPreset("power_nap", "Siesta Rápida", 20 * 60, "¡Despierta! Tu siesta reconfortante de veinte minutos ha terminado.", "hotel"),
        TimerPreset("workout", "Entrenamiento", 45 * 60, "¡Sesión de entrenamiento completada con éxito!", "fitness_center")
    )

    // AI Voice input & parsing state
    private val _isAiParsing = MutableStateFlow(false)
    val isAiParsing = _isAiParsing.asStateFlow()

    private val _parsedAiResult = MutableStateFlow<ParsedAlarmResult?>(null)
    val parsedAiResult = _parsedAiResult.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()

    // Dialog state for manual creation/edit
    private val _editingAlarm = MutableStateFlow<AlarmEntity?>(null)
    val editingAlarm = _editingAlarm.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog = _showCreateDialog.asStateFlow()

    private val _showVoiceSheet = MutableStateFlow(false)
    val showVoiceSheet = _showVoiceSheet.asStateFlow()

    private val _userFeedbackMessage = MutableStateFlow<String?>(null)
    val userFeedbackMessage = _userFeedbackMessage.asStateFlow()

    // Theme Preset State (3 formats)
    private val _appTheme = MutableStateFlow(com.example.ui.theme.AppThemePreset.fromId(repository.getSavedThemeId()))
    val appTheme = _appTheme.asStateFlow()

    private val _showThemeDialog = MutableStateFlow(false)
    val showThemeDialog = _showThemeDialog.asStateFlow()

    // Voice Engine Settings
    val useRealisticAIVoice = ttsManager.useRealisticAIVoice
    val selectedAIVoiceName = ttsManager.selectedAIVoiceName
    val availableAIVoices = ttsManager.availableAIVoices
    val activeVoiceDescription = ttsManager.activeVoiceDescription

    fun setUseRealisticAIVoice(enabled: Boolean) {
        ttsManager.setUseRealisticAIVoice(enabled)
    }

    fun setSelectedAIVoiceName(voiceName: String?) {
        ttsManager.setSelectedAIVoiceName(voiceName)
    }

    fun openThemeDialog() {
        _showThemeDialog.value = true
    }

    fun closeThemeDialog() {
        _showThemeDialog.value = false
    }

    fun setAppTheme(preset: com.example.ui.theme.AppThemePreset) {
        _appTheme.value = preset
        repository.saveThemeId(preset.id)
    }

    init {
        // Seed initial sample data if empty
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }

        // Live clock ticker
        viewModelScope.launch {
            while (true) {
                _currentTimeMillis.value = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    fun setFilter(newFilter: AlarmFilter) {
        _filter.value = newFilter
    }

    fun toggleAlarm(alarm: AlarmEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAlarm(alarm, isEnabled)
            val msg = if (isEnabled) "Alarma activada para las ${alarm.getFormattedTime()}" else "Alarma desactivada"
            _userFeedbackMessage.value = msg
        }
    }

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                repository.insertAlarm(alarm)
                _userFeedbackMessage.value = "Aviso '${alarm.title}' programado para las ${alarm.getFormattedTime()}"
            } else {
                repository.updateAlarm(alarm)
                _userFeedbackMessage.value = "Aviso '${alarm.title}' actualizado"
            }
            closeCreateEditDialog()
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            _userFeedbackMessage.value = "Aviso eliminado"
        }
    }

    fun openCreateDialog(initialType: AlarmType = AlarmType.ALARM) {
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 30) }
        _editingAlarm.value = AlarmEntity(
            title = "Nuevo Aviso",
            type = initialType,
            hour = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            repeatType = RepeatType.ONCE,
            spokenMessage = "Atención: Es la hora de tu aviso",
            isEnabled = true,
            syncToCalendar = false
        )
        _showCreateDialog.value = true
    }

    fun openEditDialog(alarm: AlarmEntity) {
        _editingAlarm.value = alarm
        _showCreateDialog.value = true
    }

    fun closeCreateEditDialog() {
        _showCreateDialog.value = false
        _editingAlarm.value = null
    }

    fun openVoiceAssistant() {
        _showVoiceSheet.value = true
        _parsedAiResult.value = null
        _aiError.value = null
    }

    fun closeVoiceAssistant() {
        _showVoiceSheet.value = false
        voiceHelper.stopListening()
    }

    fun startVoiceListening() {
        _aiError.value = null
        voiceHelper.startListening { transcript ->
            processVoiceCommandWithAi(transcript)
        }
    }

    fun stopVoiceListening() {
        voiceHelper.stopListening()
    }

    fun processVoiceCommandWithAi(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _isAiParsing.value = true
            _aiError.value = null
            try {
                val result = GeminiAlarmParser.parseUserVoicePrompt(prompt)
                _parsedAiResult.value = result
            } catch (e: Exception) {
                _aiError.value = "Error al procesar: ${e.message}"
            } finally {
                _isAiParsing.value = false
            }
        }
    }

    fun applyParsedAiResult(result: ParsedAlarmResult) {
        if (result.isTimer) {
            startTimer(result.timerDurationSeconds, result.spokenMessage, result.title)
            _userFeedbackMessage.value = "Temporizador de ${result.timerDurationSeconds / 60} min iniciado"
            closeVoiceAssistant()
        } else {
            val alarm = AlarmEntity(
                title = result.title,
                type = result.type,
                hour = result.hour,
                minute = result.minute,
                dateMillis = result.dateMillis,
                repeatType = result.repeatType,
                repeatDays = result.repeatDays,
                spokenMessage = result.spokenMessage,
                repeatSpeechCount = result.repeatSpeechCount,
                isEnabled = true,
                syncToCalendar = result.syncToCalendar
            )
            saveAlarm(alarm)
            closeVoiceAssistant()
        }
    }

    fun testSpokenAnnouncement(text: String, repeatCount: Int = 1) {
        ttsManager.speakAloud(text, repeatCount = repeatCount, ensureSpeaker = true)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun stopActiveAlarmRinging() {
        AlarmSpeakerService.stopRinging(getApplication())
    }

    fun snoozeActiveAlarm() {
        val current = ringingState.value
        if (current.isRinging) {
            val scheduler = AlarmScheduler(getApplication())
            scheduler.scheduleSnooze(current.alarmId, current.title, current.spokenText, 2, minutes = 5)
            stopActiveAlarmRinging()
            _userFeedbackMessage.value = "Alarma pospuesta 5 minutos"
        }
    }

    // --- TIMER ENGINE ---
    fun startTimer(totalSeconds: Int, spokenMessage: String = "¡El temporizador ha terminado!", label: String = "Temporizador") {
        timerJob?.cancel()
        _timerState.value = ActiveTimerState(
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            isRunning = true,
            isPaused = false,
            spokenMessage = spokenMessage,
            label = label
        )

        timerJob = viewModelScope.launch {
            while (_timerState.value.remainingSeconds > 0) {
                delay(1000)
                if (!_timerState.value.isPaused) {
                    val nextSec = _timerState.value.remainingSeconds - 1
                    _timerState.value = _timerState.value.copy(remainingSeconds = nextSec)
                }
            }

            // Timer Finished
            _timerState.value = _timerState.value.copy(isRunning = false, remainingSeconds = 0)
            onTimerCompleted(spokenMessage, label)
        }
    }

    fun pauseTimer() {
        _timerState.value = _timerState.value.copy(isPaused = true)
    }

    fun resumeTimer() {
        _timerState.value = _timerState.value.copy(isPaused = false)
    }

    fun resetTimer() {
        timerJob?.cancel()
        _timerState.value = ActiveTimerState()
    }

    fun addOneMinuteToTimer() {
        val current = _timerState.value
        _timerState.value = current.copy(
            totalSeconds = current.totalSeconds + 60,
            remainingSeconds = current.remainingSeconds + 60
        )
    }

    private fun onTimerCompleted(spokenMessage: String, label: String) {
        // Speak completion over speaker
        ttsManager.speakAloud(spokenMessage, repeatCount = 2, ensureSpeaker = true)

        // Show Notification
        val notificationManager = getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(getApplication(), VoxAlarmApplication.CHANNEL_ID_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏱️ $label Finalizado")
            .setContentText(spokenMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(5002, notification)
    }

    fun clearFeedbackMessage() {
        _userFeedbackMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        voiceHelper.stopListening()
        timerJob?.cancel()
    }
}
