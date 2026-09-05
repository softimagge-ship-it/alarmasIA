package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActiveTimerState
import com.example.data.model.TimerPreset
import com.example.ui.theme.MinimalAmber
import com.example.ui.theme.MinimalCoral

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    timerState: ActiveTimerState,
    presets: List<TimerPreset>,
    isCurrentlySpeaking: Boolean,
    onStartTimer: (totalSeconds: Int, spokenMessage: String, label: String) -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onAddMinute: () -> Unit,
    onTestVoice: (String) -> Unit,
    onStopVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customHours by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableIntStateOf(15) }
    var customSeconds by remember { mutableIntStateOf(0) }
    var customSpokenText by remember { mutableStateOf("¡El temporizador ha terminado!") }
    var customLabel by remember { mutableStateOf("Temporizador") }

    val animatedProgress by animateFloatAsState(
        targetValue = timerState.progress,
        label = "timer_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular Dial Progress
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(210.dp)
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 12.dp.toPx()
                // Track background
                drawCircle(
                    color = outlineColor.copy(alpha = 0.25f),
                    style = Stroke(width = strokeWidth)
                )

                // Animated Progress Arc
                if (timerState.isRunning || timerState.isPaused) {
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (timerState.isRunning || timerState.isPaused) {
                    Text(
                        text = timerState.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timerState.formattedRemaining(),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (timerState.isPaused) "En Pausa" else "En curso...",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (timerState.isPaused) MinimalAmber else MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = "Configurar",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val configuredSec = (customHours * 3600) + (customMinutes * 60) + customSeconds
                    val formatted = if (customHours > 0) "%02d:%02d:%02d".format(customHours, customMinutes, customSeconds)
                    else "%02d:%02d".format(customMinutes, customSeconds)
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Timer Controls: Play / Pause / Reset / +1 Min
        if (timerState.isRunning || timerState.isPaused) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onResetTimer,
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("reset_timer_btn")
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reiniciar", tint = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        if (timerState.isPaused) onResumeTimer() else onPauseTimer()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (timerState.isPaused) MaterialTheme.colorScheme.primary else MinimalCoral
                    ),
                    modifier = Modifier
                        .size(62.dp)
                        .testTag("pause_resume_timer_btn"),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (timerState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (timerState.isPaused) "Continuar" else "Pausar",
                        modifier = Modifier.size(28.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                OutlinedButton(
                    onClick = onAddMinute,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_minute_timer_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "+1 min", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+1 min", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        } else {
            // Idle State: Start Button
            Button(
                onClick = {
                    val totalSec = (customHours * 3600) + (customMinutes * 60) + customSeconds
                    if (totalSec > 0) {
                        onStartTimer(totalSec, customSpokenText, customLabel)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp)
                    .testTag("start_timer_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Iniciar", tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Iniciar Temporizador", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Preset Chips
        Text(
            text = "Presets rápidos con aviso:",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                val icon = when (preset.iconCategory) {
                    "school" -> Icons.Default.School
                    "emoji_food_beverage" -> Icons.Default.EmojiFoodBeverage
                    "restaurant" -> Icons.Default.Restaurant
                    "hotel" -> Icons.Default.Hotel
                    "fitness_center" -> Icons.Default.FitnessCenter
                    else -> Icons.Default.Timer
                }
                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            onStartTimer(preset.totalSeconds, preset.spokenMessage, preset.title)
                        }
                        .testTag("preset_${preset.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = preset.title, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = preset.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "${preset.totalSeconds / 60} min", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Custom Time & Spoken Message Setup Card (When not running)
        if (!timerState.isRunning && !timerState.isPaused) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Personalizar Tiempo y Locución",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick duration buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 5, 10, 15, 30).forEach { mins ->
                            val isSelected = customMinutes == mins && customHours == 0
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        customHours = 0
                                        customMinutes = mins
                                        customSeconds = 0
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${mins}m",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("Nombre del temporizador") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customSpokenText,
                        onValueChange = { customSpokenText = it },
                        label = { Text("Locución por altavoz al finalizar") },
                        placeholder = { Text("Ej: ¡El temporizador ha terminado!") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (isCurrentlySpeaking) onStopVoice() else onTestVoice(customSpokenText)
                            }) {
                                Icon(
                                    imageVector = if (isCurrentlySpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "Probar locución",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
