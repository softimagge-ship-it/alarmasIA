package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.ai.AIVoiceManager
import com.example.ai.AIVoiceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaker profile defining pitch, speech rate, and display name for offline/local TTS.
 */
data class SpeakerProfile(
    val id: Int,
    val name: String,
    val description: String,
    val pitch: Float,
    val speechRate: Float
)

class TTSManager private constructor(private val context: Context) : TextToSpeech.OnInitListener {

    private val aiVoiceManager = AIVoiceManager.getInstance(context)
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isLocalSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = combine(_isLocalSpeaking, aiVoiceManager.isPlayingAIVoice) { local, ai ->
        local || ai
    }.stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, false)

    private val _currentSpokenText = MutableStateFlow("")
    val currentSpokenText = _currentSpokenText.asStateFlow()

    private val _currentSpeaker = MutableStateFlow<SpeakerProfile?>(null)
    val currentSpeaker = _currentSpeaker.asStateFlow()

    // Mode: Realistic AI Voices vs Local TTS
    private val _useRealisticAIVoice = MutableStateFlow(true)
    val useRealisticAIVoice = _useRealisticAIVoice.asStateFlow()

    // Selected AI voice (or null for automatic rotation among all 4 voices)
    private val _selectedAIVoiceName = MutableStateFlow<String?>(null)
    val selectedAIVoiceName = _selectedAIVoiceName.asStateFlow()

    val availableAIVoices: List<AIVoiceProfile> = aiVoiceManager.availableAIVoices

    val activeVoiceDescription: StateFlow<String> = combine(
        _currentSpeaker,
        aiVoiceManager.currentAIVoiceName,
        _useRealisticAIVoice
    ) { speaker, aiVoice, useAi ->
        when {
            aiVoice != null -> "✨ $aiVoice"
            speaker != null -> "🎙️ ${speaker.name} (${speaker.description})"
            useAi && aiVoiceManager.isAiVoiceAvailable() -> "✨ Voz IA Gemini (Ultra-realista)"
            else -> "🎙️ Locutores del Sistema (Local)"
        }
    }.stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, "Voz Lista")

    private val scope = CoroutineScope(Dispatchers.Main)
    private var speechLoopJob: Job? = null
    private var currentUtteranceDeferred: CompletableDeferred<Unit>? = null

    // Call counter to alternate starting speaker across messages
    private val speakerCallIndex = AtomicInteger(0)

    // Available Spanish voices discovered from device TTS engine
    private val availableSpanishVoices = mutableListOf<Voice>()

    /**
     * 3 Distinct Local Speaker Personalities to alternate between:
     * 1. Locutor 1: Carlos (Tono estándar, profesional y claro)
     * 2. Locutor 2: Elena (Tono dinámico, agudo y ágil)
     * 3. Locutor 3: Marcos (Tono grave, pausado y firme)
     */
    val speakerProfiles = listOf(
        SpeakerProfile(
            id = 0,
            name = "Locutor 1 (Carlos)",
            description = "Tono estándar y profesional",
            pitch = 1.0f,
            speechRate = 0.95f
        ),
        SpeakerProfile(
            id = 1,
            name = "Locutor 2 (Elena)",
            description = "Tono dinámico y agudo",
            pitch = 1.28f,
            speechRate = 1.05f
        ),
        SpeakerProfile(
            id = 2,
            name = "Locutor 3 (Marcos)",
            description = "Tono grave y pausado",
            pitch = 0.74f,
            speechRate = 0.88f
        )
    )

    companion object {
        private const val TAG = "TTSManager"
        @Volatile
        private var INSTANCE: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TTSManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun init() {
        if (tts == null) {
            tts = TextToSpeech(context, this)
        }
    }

    fun setUseRealisticAIVoice(enabled: Boolean) {
        _useRealisticAIVoice.value = enabled
    }

    fun setSelectedAIVoiceName(voiceName: String?) {
        _selectedAIVoiceName.value = voiceName
    }

    fun isAiVoiceReady(): Boolean {
        return aiVoiceManager.isAiVoiceAvailable()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                val spanishGeneric = Locale("es")
                val resGen = tts?.setLanguage(spanishGeneric)
                if (resGen == TextToSpeech.LANG_MISSING_DATA || resGen == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
            }

            // Audio attributes for speaker alarms
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

            // Discover and index available voices if the engine has multiple
            try {
                availableSpanishVoices.clear()
                val voices = tts?.voices
                if (voices != null) {
                    val spanishVoices = voices.filter { voice ->
                        !voice.isNetworkConnectionRequired &&
                                (voice.locale.language.equals("es", ignoreCase = true) ||
                                        voice.name.contains("es-", ignoreCase = true))
                    }
                    if (spanishVoices.isNotEmpty()) {
                        availableSpanishVoices.addAll(spanishVoices)
                        Log.d(TAG, "Found ${spanishVoices.size} local Spanish voices in TTS engine")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not query voices: ${e.message}")
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch { _isLocalSpeaking.value = true }
                }

                override fun onDone(utteranceId: String?) {
                    currentUtteranceDeferred?.complete(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    currentUtteranceDeferred?.complete(Unit)
                }
            })

            isInitialized = true
            Log.d(TAG, "TTS Initialized successfully with 3 alternating speaker profiles")
        } else {
            Log.e(TAG, "TTS Initialization failed with code $status")
        }
    }

    /**
     * Speaks the text aloud via the speaker.
     * Prioritizes ultra-realistic Gemini AI Voice (with voice rotation across repetitions).
     * Falls back seamlessly to the 3-speaker local TTS if offline or unconfigured.
     */
    fun speakAloud(
        text: String,
        repeatCount: Int = 1,
        ensureSpeaker: Boolean = true,
        utteranceIdPrefix: String = "vox_alarm"
    ) {
        if (text.isBlank()) return

        stop()
        _currentSpokenText.value = text

        if (ensureSpeaker) {
            routeToSpeaker()
        }

        val shouldUseAi = _useRealisticAIVoice.value && aiVoiceManager.isAiVoiceAvailable()

        if (shouldUseAi) {
            aiVoiceManager.speakAloudWithAIVoice(
                text = text,
                repeatCount = repeatCount,
                preferredVoiceName = _selectedAIVoiceName.value,
                onFallbackToLocalTts = {
                    speakWithLocalTts(text, repeatCount, utteranceIdPrefix)
                }
            )
        } else {
            speakWithLocalTts(text, repeatCount, utteranceIdPrefix)
        }
    }

    private fun speakWithLocalTts(text: String, repeatCount: Int, utteranceIdPrefix: String) {
        if (!isInitialized || tts == null) {
            tts = TextToSpeech(context) { status ->
                onInit(status)
                if (status == TextToSpeech.SUCCESS) {
                    startAlternatingSpeechLoop(text, repeatCount, utteranceIdPrefix)
                }
            }
        } else {
            startAlternatingSpeechLoop(text, repeatCount, utteranceIdPrefix)
        }
    }

    private fun startAlternatingSpeechLoop(text: String, repeatCount: Int, utteranceIdPrefix: String) {
        speechLoopJob?.cancel()
        speechLoopJob = scope.launch {
            _isLocalSpeaking.value = true
            val startingSpeakerIdx = speakerCallIndex.getAndIncrement()

            try {
                for (i in 0 until repeatCount) {
                    val profileIdx = (startingSpeakerIdx + i) % speakerProfiles.size
                    val profile = speakerProfiles[profileIdx]
                    _currentSpeaker.value = profile

                    applySpeakerProfile(profile, profileIdx)

                    val utteranceId = "${utteranceIdPrefix}_rep${i}_${System.currentTimeMillis()}"
                    val deferred = CompletableDeferred<Unit>()
                    currentUtteranceDeferred = deferred

                    val params = Bundle().apply {
                        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                    }

                    val speakResult = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (speakResult == TextToSpeech.ERROR) {
                        Log.e(TAG, "Error triggering speak for ${profile.name}")
                        break
                    }

                    val estimatedDurationMs = ((text.length * 75L) / profile.speechRate).toLong().coerceAtLeast(1500L)
                    withTimeoutOrNull(estimatedDurationMs + 3000L) {
                        deferred.await()
                    }

                    if (i < repeatCount - 1) {
                        delay(5000L) // 5 segundos entre cada locución
                    }
                }
            } catch (e: CancellationException) {
                // Playback intentionally cancelled
            } catch (e: Exception) {
                Log.w(TAG, "Speech loop error: ${e.message}")
            } finally {
                _isLocalSpeaking.value = false
                _currentSpokenText.value = ""
                _currentSpeaker.value = null
                currentUtteranceDeferred = null
            }
        }
    }

    private fun applySpeakerProfile(profile: SpeakerProfile, index: Int) {
        tts?.setPitch(profile.pitch)
        tts?.setSpeechRate(profile.speechRate)

        if (availableSpanishVoices.isNotEmpty()) {
            val assignedVoice = availableSpanishVoices[index % availableSpanishVoices.size]
            try {
                tts?.voice = assignedVoice
            } catch (e: Exception) {
                Log.w(TAG, "Could not set voice: ${e.message}")
            }
        }
    }

    fun stop() {
        aiVoiceManager.stop()
        speechLoopJob?.cancel()
        currentUtteranceDeferred?.complete(Unit)
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
        _isLocalSpeaking.value = false
        _currentSpokenText.value = ""
        _currentSpeaker.value = null
    }

    private fun routeToSpeaker() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not force speakerphone: ${e.message}")
        }
    }
}
