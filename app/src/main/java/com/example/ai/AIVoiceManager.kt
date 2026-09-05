package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Realistic Gemini AI Voice profile.
 */
data class AIVoiceProfile(
    val id: String,
    val voiceName: String,
    val displayName: String,
    val description: String,
    val gender: String
)

class AIVoiceManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _isPlayingAIVoice = MutableStateFlow(false)
    val isPlayingAIVoice = _isPlayingAIVoice.asStateFlow()

    private val _currentAIVoiceName = MutableStateFlow<String?>(null)
    val currentAIVoiceName = _currentAIVoiceName.asStateFlow()

    private var activePlaybackJob: Job? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private val voiceRotationCounter = AtomicInteger(0)

    val availableAIVoices = listOf(
        AIVoiceProfile("kore", "Kore", "Kore (Voz IA)", "Cálida, natural y empática", "Femenina"),
        AIVoiceProfile("puck", "Puck", "Puck (Voz IA)", "Enérgica, entusiasta y clara", "Masculina"),
        AIVoiceProfile("fenrir", "Fenrir", "Fenrir (Voz IA)", "Profunda, autoritaria y firme", "Masculina"),
        AIVoiceProfile("aoede", "Aoede", "Aoede (Voz IA)", "Suave, relajante y melódica", "Femenina")
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AIVoiceManager"
        private const val MODEL_NAME = "gemini-2.5-flash-preview-tts"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

        @Volatile
        private var INSTANCE: AIVoiceManager? = null

        fun getInstance(context: Context): AIVoiceManager {
            return INSTANCE ?: synchronized(this) {
                val instance = AIVoiceManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Checks if Gemini API key is configured.
     */
    fun isAiVoiceAvailable(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Synthesizes and plays text using ultra-realistic Gemini AI Voice.
     * Alternates across distinct AI voices on each repeat.
     */
    fun speakAloudWithAIVoice(
        text: String,
        repeatCount: Int = 1,
        preferredVoiceName: String? = null,
        onFallbackToLocalTts: () -> Unit
    ) {
        if (text.isBlank()) return

        stop()

        activePlaybackJob = scope.launch {
            _isPlayingAIVoice.value = true
            val startingIdx = voiceRotationCounter.getAndIncrement()

            try {
                routeToSpeaker()

                for (i in 0 until repeatCount) {
                    val voice = if (preferredVoiceName != null && preferredVoiceName.isNotBlank()) {
                        availableAIVoices.find { it.voiceName.equals(preferredVoiceName, ignoreCase = true) }
                            ?: availableAIVoices[0]
                    } else {
                        availableAIVoices[(startingIdx + i) % availableAIVoices.size]
                    }

                    _currentAIVoiceName.value = "${voice.displayName} (${voice.description})"

                    val audioFile = getOrSynthesizeAIVoiceAudio(text, voice.voiceName)

                    if (audioFile != null && audioFile.exists() && audioFile.length() > 100) {
                        playAudioFile(audioFile)
                        if (i < repeatCount - 1) {
                            delay(5000L) // 5 segundos entre cada locución
                        }
                    } else {
                        Log.w(TAG, "AI voice synthesis returned null, falling back to local TTS engine")
                        _isPlayingAIVoice.value = false
                        _currentAIVoiceName.value = null
                        onFallbackToLocalTts()
                        return@launch
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "AI voice playback coroutine cancelled normally")
            } catch (e: Exception) {
                Log.e(TAG, "Error playing AI Voice: ${e.message}", e)
                _isPlayingAIVoice.value = false
                _currentAIVoiceName.value = null
                onFallbackToLocalTts()
            } finally {
                _isPlayingAIVoice.value = false
                _currentAIVoiceName.value = null
            }
        }
    }

    /**
     * Synthesizes speech using Gemini 2.5 Flash TTS with audio modality.
     * Returns a local WAV/MP3 file, caching identical requests.
     */
    suspend fun getOrSynthesizeAIVoiceAudio(text: String, voiceName: String = "Kore"): File? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured for AI Voice")
            return@withContext null
        }

        val cacheKey = md5Hash("$text-$voiceName")
        val cacheFile = File(context.cacheDir, "ai_voice_$cacheKey.wav")
        if (cacheFile.exists() && cacheFile.length() > 500) {
            return@withContext cacheFile
        }

        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", "Lee en español con entonación natural, clara, amigable y expresiva: $text"))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                val genConfig = JSONObject().apply {
                    val modalities = JSONArray().apply {
                        put("AUDIO")
                    }
                    put("responseModalities", modalities)

                    val speechConfig = JSONObject().apply {
                        val voiceConfig = JSONObject().apply {
                            val prebuiltVoiceConfig = JSONObject().apply {
                                put("voiceName", voiceName)
                            }
                            put("prebuiltVoiceConfig", prebuiltVoiceConfig)
                        }
                        put("voiceConfig", voiceConfig)
                    }
                    put("speechConfig", speechConfig)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "AI voice request failed with HTTP ${response.code}: $responseString")
                return@withContext null
            }

            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)

            val inlineData = firstPart?.optJSONObject("inlineData")
            if (inlineData == null) {
                Log.w(TAG, "No inlineData received from Gemini TTS response")
                return@withContext null
            }

            val mimeType = inlineData.optString("mimeType", "audio/wav")
            val base64Data = inlineData.optString("data", "")
            if (base64Data.isBlank()) {
                Log.w(TAG, "Empty base64 audio data received")
                return@withContext null
            }

            val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)

            // If raw PCM format, wrap with WAV header for seamless MediaPlayer playback
            val finalAudioBytes = if (mimeType.contains("pcm", ignoreCase = true) || !isWavHeaderPresent(rawBytes)) {
                pcmToWav(rawBytes, sampleRate = 24000, channels = 1, bitsPerSample = 16)
            } else {
                rawBytes
            }

            FileOutputStream(cacheFile).use { fos ->
                fos.write(finalAudioBytes)
                fos.flush()
            }

            Log.d(TAG, "Successfully synthesized and cached AI voice ($voiceName, ${finalAudioBytes.size} bytes)")
            cacheFile
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synthesize AI Voice: ${e.message}", e)
            null
        }
    }

    private suspend fun playAudioFile(file: File) = withContext(Dispatchers.Main) {
        val completionDeferred = CompletableDeferred<Unit>()
        val mp = MediaPlayer()
        activeMediaPlayer = mp

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            mp.setAudioAttributes(audioAttributes)
            mp.setDataSource(file.absolutePath)
            mp.setVolume(1.0f, 1.0f)

            mp.setOnCompletionListener {
                completionDeferred.complete(Unit)
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                completionDeferred.complete(Unit)
                true
            }

            mp.prepare()
            mp.start()

            // Wait for audio playback to finish (or max timeout)
            val duration = mp.duration.toLong().coerceAtLeast(1000L)
            withTimeoutOrNull(duration + 4000L) {
                completionDeferred.await()
            }
        } catch (e: CancellationException) {
            // Normal cancellation when stop is pressed
        } catch (e: Exception) {
            Log.e(TAG, "Error in MediaPlayer: ${e.message}")
        } finally {
            try {
                if (mp.isPlaying) {
                    mp.stop()
                }
            } catch (_: Exception) {}
            try {
                mp.release()
            } catch (_: Exception) {}
            if (activeMediaPlayer == mp) {
                activeMediaPlayer = null
            }
        }
    }

    fun stop() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (_: Exception) {}
        activeMediaPlayer = null
        _isPlayingAIVoice.value = false
        _currentAIVoiceName.value = null
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

    private fun isWavHeaderPresent(data: ByteArray): Boolean {
        if (data.size < 12) return false
        return data[0] == 'R'.code.toByte() &&
                data[1] == 'I'.code.toByte() &&
                data[2] == 'F'.code.toByte() &&
                data[3] == 'F'.code.toByte() &&
                data[8] == 'W'.code.toByte() &&
                data[9] == 'A'.code.toByte() &&
                data[10] == 'V'.code.toByte() &&
                data[11] == 'E'.code.toByte()
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int = 24000, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        return header + pcmData
    }

    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
