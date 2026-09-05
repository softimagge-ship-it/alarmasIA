package com.example.data.model

data class TimerPreset(
    val id: String,
    val title: String,
    val totalSeconds: Int,
    val spokenMessage: String,
    val iconCategory: String
)

data class ActiveTimerState(
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val spokenMessage: String = "¡El temporizador ha finalizado!",
    val label: String = "Temporizador"
) {
    val progress: Float
        get() = if (totalSeconds > 0) (remainingSeconds.toFloat() / totalSeconds.toFloat()) else 0f

    fun formattedRemaining(): String {
        val hours = remainingSeconds / 3600
        val minutes = (remainingSeconds % 3600) / 60
        val seconds = remainingSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
