package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class VoiceRecognizerHelper(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript = _liveTranscript.asStateFlow()

    private val _soundLevel = MutableStateFlow(0f)
    val soundLevel = _soundLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var onResultCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "VoiceRecognizerHelper"
    }

    fun startListening(onResult: (String) -> Unit) {
        onResultCallback = onResult
        _errorMessage.value = null
        _liveTranscript.value = ""

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorMessage.value = "Reconocimiento de voz no disponible en este dispositivo"
            return
        }

        try {
            stopListening()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        Log.d(TAG, "Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        _soundLevel.value = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _soundLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _soundLevel.value = 0f
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Error de grabación de audio"
                            SpeechRecognizer.ERROR_CLIENT -> "Error de cliente de reconocimiento"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono no concedido"
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Error de conexión de red"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció ninguna frase, intenta de nuevo"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor está ocupado"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
                            else -> "Error en el reconocimiento de voz ($error)"
                        }
                        Log.w(TAG, "SpeechRecognizer error: $msg")
                        _errorMessage.value = msg
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _soundLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            _liveTranscript.value = text
                            onResultCallback?.invoke(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            _liveTranscript.value = text
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para configurar tu alarma o aviso...")
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
            _errorMessage.value = "Error al iniciar micrófono: ${e.message}"
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer", e)
        }
        speechRecognizer = null
        _isListening.value = false
        _soundLevel.value = 0f
    }
}
