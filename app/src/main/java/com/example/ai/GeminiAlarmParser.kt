package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.AlarmType
import com.example.data.model.RepeatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedAlarmResult(
    val isTimer: Boolean = false,
    val timerDurationSeconds: Int = 0,
    val title: String = "",
    val type: AlarmType = AlarmType.ALARM,
    val hour: Int = 8,
    val minute: Int = 0,
    val dateMillis: Long? = null,
    val repeatType: RepeatType = RepeatType.ONCE,
    val repeatDays: String = "",
    val spokenMessage: String = "",
    val syncToCalendar: Boolean = false,
    val repeatSpeechCount: Int = 2,
    val aiSummary: String = "",
    val rawPrompt: String = ""
)

object GeminiAlarmParser {

    private const val TAG = "GeminiAlarmParser"
    private const val MODEL_NAME = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun parseUserVoicePrompt(prompt: String): ParsedAlarmResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "Gemini API key is not configured; using smart local rule-based parsing")
            return@withContext parseLocalFallback(prompt)
        }

        try {
            val now = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale("es", "ES"))
            val currentDateStr = dateFormat.format(now.time)

            val systemInstruction = """
                Eres un asistente experto para una app de alarmas, avisos y temporizadores con locución por altavoz en español.
                La fecha y hora actual es: $currentDateStr.
                El usuario te dictará un aviso, alarma o temporizador en lenguaje natural.
                Tu tarea es extraer los parámetros estructurados y devolver estrictamente un JSON válido con esta estructura:
                {
                   "isTimer": boolean (true si pide temporizador o cuenta regresiva de X minutos/segundos),
                   "timerDurationSeconds": integer (segundos totales si es temporizador, ej: 900 para 15 min),
                   "title": string (título descriptivo y conciso en español),
                   "type": "ALARM" | "REMINDER" | "EVENT",
                   "hour": integer (0 a 23),
                   "minute": integer (0 a 59),
                   "date": "YYYY-MM-DD" o null (si es para un día específico o null si es repetitivo o para la próxima ocurrencia),
                   "repeatType": "ONCE" | "DAILY" | "WEEKDAYS" | "WEEKENDS" | "CUSTOM",
                   "repeatDays": string (días separados por coma ej: "1,2,3,4,5" donde 1=Lunes .. 7=Domingo),
                   "spokenMessage": string (el texto exacto que el altavoz locutará al sonar, debe sonar natural, claro y en español),
                   "syncToCalendar": boolean (true si menciona calendario o es una reunión/cita importante),
                   "repeatSpeechCount": integer (entre 1 y 5, número de veces que el altavoz repetirá la locución, por defecto 2 o el número indicado ej: 5 si dice 'x5' o '5 veces'),
                   "aiSummary": string (explicación breve y amigable en español de lo que se ha configurado)
                }
                Si el usuario no especifica frase de locución, genera una frase clara y amable para el altavoz basada en el título.
                Devuelve ÚNICAMENTE el bloque JSON.
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArray = org.json.JSONArray()
                val contentObj = JSONObject()
                val partsArray = org.json.JSONArray()
                partsArray.put(JSONObject().put("text", "$systemInstruction\n\nInstrucción del usuario:\n$prompt"))
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API error: ${response.code} -> $responseString")
                return@withContext parseLocalFallback(prompt)
            }

            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates")
            val candidate = candidates?.optJSONObject(0)
            val content = candidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

            val cleanedJson = cleanJsonString(rawText)
            parseJsonToResult(cleanedJson, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini parsing: ${e.message}", e)
            parseLocalFallback(prompt)
        }
    }

    private fun cleanJsonString(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.removePrefix("```json").trim()
        }
        if (clean.startsWith("```")) {
            clean = clean.removePrefix("```").trim()
        }
        if (clean.endsWith("```")) {
            clean = clean.removeSuffix("```").trim()
        }
        return clean
    }

    private fun parseJsonToResult(jsonStr: String, prompt: String): ParsedAlarmResult {
        return try {
            val obj = JSONObject(jsonStr)
            val isTimer = obj.optBoolean("isTimer", false)
            val timerDurationSeconds = obj.optInt("timerDurationSeconds", 0)
            val title = obj.optString("title", "Aviso VoxAlarm")
            val typeStr = obj.optString("type", "ALARM")
            val type = try { AlarmType.valueOf(typeStr) } catch (_: Exception) { AlarmType.ALARM }

            val hour = obj.optInt("hour", 8)
            val minute = obj.optInt("minute", 0)
            val dateStr = obj.optString("date", "")
            val repeatTypeStr = obj.optString("repeatType", "ONCE")
            val repeatType = try { RepeatType.valueOf(repeatTypeStr) } catch (_: Exception) { RepeatType.ONCE }
            val repeatDays = obj.optString("repeatDays", "")
            val spokenMessage = obj.optString("spokenMessage", "Atención: $title")
            val syncToCalendar = obj.optBoolean("syncToCalendar", false)
            val repeatSpeechCount = obj.optInt("repeatSpeechCount", 2).coerceIn(1, 5)
            val aiSummary = obj.optString("aiSummary", "Configurado aviso para las %02d:%02d".format(hour, minute))

            var dateMillis: Long? = null
            if (dateStr.isNotBlank() && dateStr != "null") {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsed = sdf.parse(dateStr)
                    if (parsed != null) {
                        dateMillis = parsed.time
                    }
                } catch (_: Exception) {}
            }

            ParsedAlarmResult(
                isTimer = isTimer,
                timerDurationSeconds = timerDurationSeconds,
                title = title,
                type = type,
                hour = hour,
                minute = minute,
                dateMillis = dateMillis,
                repeatType = repeatType,
                repeatDays = repeatDays,
                spokenMessage = spokenMessage,
                syncToCalendar = syncToCalendar,
                repeatSpeechCount = repeatSpeechCount,
                aiSummary = aiSummary,
                rawPrompt = prompt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON result: ${e.message}")
            parseLocalFallback(prompt)
        }
    }

    /**
     * Fallback parser using regex & natural language heuristics for Spanish.
     */
    fun parseLocalFallback(prompt: String): ParsedAlarmResult {
        val lower = prompt.lowercase(Locale("es", "ES"))
        val now = Calendar.getInstance()

        // Check if it is a timer (e.g. "temporizador de 15 minutos", "timer 5 min", "cuenta atras 30 seg")
        if (lower.contains("temporizador") || lower.contains("timer") || lower.contains("cuenta atrás") || lower.contains("cuenta atras")) {
            val minPattern = Pattern.compile("(\\d+)\\s*(minuto|minutos|min|mins)")
            val minMatcher = minPattern.matcher(lower)
            var totalSec = 0
            if (minMatcher.find()) {
                val mins = minMatcher.group(1)?.toIntOrNull() ?: 0
                totalSec += mins * 60
            }
            val secPattern = Pattern.compile("(\\d+)\\s*(segundo|segundos|seg|secs)")
            val secMatcher = secPattern.matcher(lower)
            if (secMatcher.find()) {
                val secs = secMatcher.group(1)?.toIntOrNull() ?: 0
                totalSec += secs
            }
            if (totalSec == 0) totalSec = 300 // default 5 min

            val spoken = if (lower.contains("que diga")) {
                prompt.substringAfter("que diga").trim().trim('"', '\'')
            } else if (lower.contains("di ")) {
                prompt.substringAfter("di ").trim().trim('"', '\'')
            } else {
                "¡El temporizador ha terminado!"
            }

            return ParsedAlarmResult(
                isTimer = true,
                timerDurationSeconds = totalSec,
                title = "Temporizador (${totalSec / 60}m)",
                spokenMessage = spoken,
                aiSummary = "Temporizador de ${totalSec / 60} minutos con aviso hablado.",
                rawPrompt = prompt
            )
        }

        // Time extraction (e.g. "a las 7:30", "a las 18:00", "a las 8 de la mañana", "a las 9 y media")
        var hour = 8
        var minute = 0

        val timePattern = Pattern.compile("(\\d{1,2})[:.](\\d{2})")
        val timeMatcher = timePattern.matcher(lower)
        if (timeMatcher.find()) {
            hour = timeMatcher.group(1)?.toIntOrNull() ?: 8
            minute = timeMatcher.group(2)?.toIntOrNull() ?: 0
        } else {
            val simpleHourPattern = Pattern.compile("a las (\\d{1,2})")
            val simpleHourMatcher = simpleHourPattern.matcher(lower)
            if (simpleHourMatcher.find()) {
                hour = simpleHourMatcher.group(1)?.toIntOrNull() ?: 8
                if (lower.contains("y media")) minute = 30
                else if (lower.contains("y cuarto")) minute = 15
                else if (lower.contains("menos cuarto")) {
                    hour = (hour - 1 + 24) % 24
                    minute = 45
                }
            }
        }

        if (lower.contains("tarde") || lower.contains("noche")) {
            if (hour in 1..11) hour += 12
        }

        // Repetition extraction
        var repeatType = RepeatType.ONCE
        var repeatDays = ""
        if (lower.contains("todos los días") || lower.contains("cada día") || lower.contains("diario") || lower.contains("diariamente")) {
            repeatType = RepeatType.DAILY
        } else if (lower.contains("lunes a viernes") || lower.contains("dias laborables") || lower.contains("días laborables")) {
            repeatType = RepeatType.WEEKDAYS
        } else if (lower.contains("fines de semana") || lower.contains("fin de semana")) {
            repeatType = RepeatType.WEEKENDS
        } else if (lower.contains("lunes")) {
            repeatType = RepeatType.CUSTOM
            repeatDays = "1"
        } else if (lower.contains("martes")) {
            repeatType = RepeatType.CUSTOM
            repeatDays = "2"
        } else if (lower.contains("miércoles") || lower.contains("miercoles")) {
            repeatType = RepeatType.CUSTOM
            repeatDays = "3"
        } else if (lower.contains("jueves")) {
            repeatType = RepeatType.CUSTOM
            repeatDays = "4"
        } else if (lower.contains("viernes")) {
            repeatType = RepeatType.CUSTOM
            repeatDays = "5"
        }

        // Calendar sync check
        val syncCalendar = lower.contains("calendario") || lower.contains("reunión") || lower.contains("reunion") || lower.contains("cita") || lower.contains("médico")

        // Spoken announcement text extraction
        var spoken = ""
        if (lower.contains("que diga")) {
            spoken = prompt.substringAfter("que diga").trim().trim('"', '\'')
        } else if (lower.contains("locuta")) {
            spoken = prompt.substringAfter("locuta").trim().trim('"', '\'')
        } else if (lower.contains("avisa")) {
            spoken = prompt.substringAfter("avisa").trim().trim('"', '\'')
        } else if (lower.contains("di ")) {
            spoken = prompt.substringAfter("di ").trim().trim('"', '\'')
        }

        val title = when {
            lower.contains("medicaci") || lower.contains("pastilla") -> "Tomar Medicación"
            lower.contains("reuni") -> "Reunión de trabajo"
            lower.contains("cita") || lower.contains("médico") -> "Cita Médica"
            lower.contains("despertar") || lower.contains("levantar") -> "Despertador"
            lower.contains("entrenar") || lower.contains("gimnasio") -> "Entrenamiento"
            else -> "Aviso VoxAlarm"
        }

        if (spoken.isBlank()) {
            spoken = "Atención: Es hora de $title"
        }

        // Speech repetitions check (1 to 5 times)
        var speechRepeatCount = 2
        val repRegex = Regex("(?:repite|repítelo|repetir|locución|locuciones|veces)\\s*(?:de)?\\s*([1-5])\\s*(?:veces|x)?", RegexOption.IGNORE_CASE)
        val repMatch = repRegex.find(lower)
        if (repMatch != null) {
            speechRepeatCount = repMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 5) ?: 2
        } else if (lower.contains("5 veces") || lower.contains("x5") || lower.contains("5 locuciones") || lower.contains("5x")) {
            speechRepeatCount = 5
        } else if (lower.contains("4 veces") || lower.contains("x4") || lower.contains("4 locuciones") || lower.contains("4x")) {
            speechRepeatCount = 4
        } else if (lower.contains("3 veces") || lower.contains("x3") || lower.contains("3 locuciones") || lower.contains("3x")) {
            speechRepeatCount = 3
        } else if (lower.contains("1 vez") || lower.contains("x1") || lower.contains("1 locución") || lower.contains("1x")) {
            speechRepeatCount = 1
        }

        return ParsedAlarmResult(
            isTimer = false,
            title = title,
            type = if (lower.contains("alarma") || lower.contains("despertar")) AlarmType.ALARM else AlarmType.REMINDER,
            hour = hour,
            minute = minute,
            repeatType = repeatType,
            repeatDays = repeatDays,
            spokenMessage = spoken,
            syncToCalendar = syncCalendar,
            repeatSpeechCount = speechRepeatCount,
            aiSummary = "Aviso '$title' para las %02d:%02d (%s, %dx)".format(hour, minute, repeatType.name, speechRepeatCount),
            rawPrompt = prompt
        )
    }
}
