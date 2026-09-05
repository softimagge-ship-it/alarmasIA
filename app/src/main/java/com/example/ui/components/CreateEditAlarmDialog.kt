package com.example.ui.components

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AlarmEntity
import com.example.data.model.AlarmType
import com.example.data.model.RepeatType
import com.example.ui.theme.MinimalCoral
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateEditAlarmDialog(
    initialAlarm: AlarmEntity?,
    isCurrentlySpeaking: Boolean,
    onSave: (AlarmEntity) -> Unit,
    onDismiss: () -> Unit,
    onTestVoice: (String, Int) -> Unit,
    onStopVoice: () -> Unit
) {
    val context = LocalContext.current
    val isEdit = initialAlarm != null && initialAlarm.id != 0L

    var title by remember { mutableStateOf(initialAlarm?.title ?: "") }
    var type by remember { mutableStateOf(initialAlarm?.type ?: AlarmType.ALARM) }
    var hour by remember { mutableIntStateOf(initialAlarm?.hour ?: 8) }
    var minute by remember { mutableIntStateOf(initialAlarm?.minute ?: 0) }
    var dateMillis by remember { mutableStateOf(initialAlarm?.dateMillis) }
    var repeatType by remember { mutableStateOf(initialAlarm?.repeatType ?: RepeatType.ONCE) }
    var repeatDays by remember { mutableStateOf(initialAlarm?.repeatDays ?: "") }
    var spokenMessage by remember { mutableStateOf(initialAlarm?.spokenMessage ?: "") }
    var syncToCalendar by remember { mutableStateOf(initialAlarm?.syncToCalendar ?: (type == AlarmType.EVENT)) }
    var repeatSpeechCount by remember { mutableIntStateOf(initialAlarm?.repeatSpeechCount ?: 2) }
    var soundVibration by remember { mutableStateOf(initialAlarm?.soundVibration ?: true) }

    var showDatePicker by remember { mutableStateOf(false) }

    val daysOfWeek = listOf(
        1 to "L", 2 to "M", 3 to "X",
        4 to "J", 5 to "V", 6 to "S", 7 to "D"
    )
    val selectedDaysSet = remember(repeatDays) {
        repeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .imePadding()
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .testTag("create_edit_alarm_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Fixed Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEdit) "Editar Aviso" else "Crear Nuevo Aviso",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                // 2. Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Type selector segmented bar
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Triple(AlarmType.ALARM, "Alarma", Icons.Default.Alarm),
                                Triple(AlarmType.REMINDER, "Aviso", Icons.Default.Notifications),
                                Triple(AlarmType.EVENT, "Calendario", Icons.Default.CalendarMonth)
                            ).forEach { (itemType, label, icon) ->
                                val isSelected = type == itemType
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        )
                                        .clickable {
                                            type = itemType
                                            if (itemType == AlarmType.EVENT) syncToCalendar = true
                                        }
                                        .padding(vertical = 8.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title input
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            if (spokenMessage.isBlank() && it.isNotBlank()) {
                                spokenMessage = "Atención: Es hora de $it"
                            }
                        },
                        label = { Text("Título del aviso / alarma") },
                        placeholder = { Text("Ej: Tomar medicación, Despertador, Reunión...") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alarm_title_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Time Picker Button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .clickable {
                                val timePicker = TimePickerDialog(
                                    context,
                                    { _, h, m ->
                                        hour = h
                                        minute = m
                                    },
                                    hour,
                                    minute,
                                    true
                                )
                                timePicker.show()
                            }
                            .testTag("time_picker_btn"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Hora",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hora programada",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "%02d:%02d".format(hour, minute),
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Text(
                                text = "Cambiar",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Recurrence Chips
                    Text(
                        text = "Repetición",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = repeatType == RepeatType.ONCE,
                            onClick = { repeatType = RepeatType.ONCE },
                            label = { Text("Una vez", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = repeatType == RepeatType.DAILY,
                            onClick = { repeatType = RepeatType.DAILY },
                            label = { Text("Diario", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = repeatType == RepeatType.WEEKDAYS,
                            onClick = { repeatType = RepeatType.WEEKDAYS },
                            label = { Text("Lun a Vie", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = repeatType == RepeatType.WEEKENDS,
                            onClick = { repeatType = RepeatType.WEEKENDS },
                            label = { Text("Fin de semana", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = repeatType == RepeatType.CUSTOM,
                            onClick = { repeatType = RepeatType.CUSTOM },
                            label = { Text("Personalizado", fontSize = 12.sp) }
                        )
                    }

                    // Custom days selector if CUSTOM is selected
                    AnimatedVisibility(visible = repeatType == RepeatType.CUSTOM) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "Días activos:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                daysOfWeek.forEach { (dayIndex, dayLabel) ->
                                    val isSelected = selectedDaysSet.contains(dayIndex)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val newSet = if (isSelected) {
                                                    selectedDaysSet - dayIndex
                                                } else {
                                                    selectedDaysSet + dayIndex
                                                }
                                                repeatDays = newSet.sorted().joinToString(",")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Date Picker if ONCE is selected
                    AnimatedVisibility(visible = repeatType == RepeatType.ONCE) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Fecha",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val dateText = if (dateMillis != null) {
                                    val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
                                    sdf.format(dateMillis!!)
                                } else {
                                    "Fecha: Próxima ocurrencia"
                                }
                                Text(
                                    text = dateText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Spoken Message (Locución por Altavoz)
                    Text(
                        text = "Locución por Altavoz (TTS)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mensaje que dirá el altavoz al sonar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = spokenMessage,
                        onValueChange = { spokenMessage = it },
                        label = { Text("Texto que locutará el altavoz") },
                        placeholder = { Text("Ej: ¡Buenos días! Es hora de levantarse...") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("spoken_message_input"),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Test TTS & Repeat count selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val textToSpeak = if (spokenMessage.isNotBlank()) spokenMessage else title
                                    if (isCurrentlySpeaking) onStopVoice() else onTestVoice(textToSpeak, repeatSpeechCount)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isCurrentlySpeaking) MinimalCoral else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("dialog_test_tts_btn")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlySpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "Probar",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isCurrentlySpeaking) "Detener" else "Probar",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // Repeat speech count selector (1x to 5x) - compact and guaranteed to show all 5 options
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Repetir:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                                listOf(1, 2, 3, 4, 5).forEach { count ->
                                    val isSel = repeatSpeechCount == count
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { repeatSpeechCount = count }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("repeat_speech_${count}x"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${count}x",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            ),
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Locutores & AI Voice Note
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Voz IA Realista & Locutores alternantes por repetición",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Calendar Integration Toggle
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Calendario",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Sincronizar en Calendario",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Guarda el evento en el calendario local",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = syncToCalendar,
                                onCheckedChange = { syncToCalendar = it },
                                modifier = Modifier.testTag("sync_calendar_switch")
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 1.dp)

                // 3. Fixed Bottom Action Buttons (Never Hidden)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalTitle = if (title.isNotBlank()) title else "Aviso VoxAlarm"
                            val finalSpoken = if (spokenMessage.isNotBlank()) spokenMessage else "Atención: Es hora de $finalTitle"
                            val alarmToSave = (initialAlarm ?: AlarmEntity(
                                hour = hour,
                                minute = minute,
                                title = finalTitle,
                                spokenMessage = finalSpoken
                            )).copy(
                                title = finalTitle,
                                type = type,
                                hour = hour,
                                minute = minute,
                                dateMillis = dateMillis,
                                repeatType = repeatType,
                                repeatDays = repeatDays,
                                spokenMessage = finalSpoken,
                                syncToCalendar = syncToCalendar,
                                repeatSpeechCount = repeatSpeechCount,
                                soundVibration = soundVibration,
                                isEnabled = true
                            )
                            onSave(alarmToSave)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("save_alarm_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Guardar", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEdit) "Guardar Cambios" else "Programar Aviso", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Material 3 Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateMillis = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
