package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.CalendarHelper
import com.example.ui.components.ActiveAlarmBanner
import com.example.ui.components.AlarmCard
import com.example.ui.components.ClockOverviewHeader
import com.example.ui.components.CreateEditAlarmDialog
import com.example.ui.components.ThemeConfigBottomSheet
import com.example.ui.components.TimerScreen
import com.example.ui.components.VoiceAiBottomSheet
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AlarmFilter
import com.example.viewmodel.AlarmViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val appTheme by viewModel.appTheme.collectAsState()
            MyApplicationTheme(themePreset = appTheme) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: AlarmViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // State Collection
    val currentTimeMillis by viewModel.currentTimeMillis.collectAsState()
    val allAlarms by viewModel.allAlarms.collectAsState()
    val filteredAlarms by viewModel.filteredAlarms.collectAsState()
    val currentFilter by viewModel.filter.collectAsState()
    val ringingState by viewModel.ringingState.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()

    val isAiParsing by viewModel.isAiParsing.collectAsState()
    val parsedAiResult by viewModel.parsedAiResult.collectAsState()
    val aiError by viewModel.aiError.collectAsState()

    val showCreateDialog by viewModel.showCreateDialog.collectAsState()
    val editingAlarm by viewModel.editingAlarm.collectAsState()
    val showVoiceSheet by viewModel.showVoiceSheet.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showVoiceEngineDialog by remember { mutableStateOf(false) }

    val useRealisticAIVoice by viewModel.useRealisticAIVoice.collectAsState()
    val selectedAIVoiceName by viewModel.selectedAIVoiceName.collectAsState()

    val isListening by viewModel.voiceHelper.isListening.collectAsState()
    val liveTranscript by viewModel.voiceHelper.liveTranscript.collectAsState()
    val soundLevel by viewModel.voiceHelper.soundLevel.collectAsState()

    val isCurrentlySpeaking by viewModel.ttsManager.isSpeaking.collectAsState()
    val currentSpokenText by viewModel.ttsManager.currentSpokenText.collectAsState()

    val feedbackMessage by viewModel.userFeedbackMessage.collectAsState()

    var selectedMainTab by remember { mutableIntStateOf(0) } // 0: Alarmas/Avisos, 1: Temporizador

    // Current Date Formatting for Top Bar
    val cal = remember(currentTimeMillis) {
        Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
    }
    val headerDateFormat = remember { SimpleDateFormat("EEEE, d MMM", Locale("es", "ES")) }
    val formattedHeaderDate = headerDateFormat.format(cal.time).replaceFirstChar { it.uppercase() }

    // Permission Launchers
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val neededPermissions = mutableListOf<String>()
        neededPermissions.add(Manifest.permission.RECORD_AUDIO)
        neededPermissions.add(Manifest.permission.READ_CALENDAR)
        neededPermissions.add(Manifest.permission.WRITE_CALENDAR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(neededPermissions.toTypedArray())
    }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedbackMessage()
        }
    }

    val nextUpcoming = remember(allAlarms, currentTimeMillis) {
        allAlarms.filter { it.isEnabled && it.nextTriggerTimeMillis > currentTimeMillis }
            .minByOrNull { it.nextTriggerTimeMillis }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_icon_square),
                            contentDescription = "Icono VoxAlarm",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = formattedHeaderDate,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "VoxAlarm AI",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    // Voice Engine & Speakers Settings Button
                    IconButton(
                        onClick = { showVoiceEngineDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("voice_engine_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Configurar motor de voz IA y locutores",
                            tint = if (useRealisticAIVoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Theme Switcher Button
                    IconButton(
                        onClick = { showThemeDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("theme_config_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Configurar tema de color",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Voice Assistant Button
                    IconButton(
                        onClick = { viewModel.openVoiceAssistant() },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("top_bar_voice_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Asistente de Voz IA",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = selectedMainTab == 0,
                    onClick = { selectedMainTab = 0 },
                    icon = {
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = "Alarmas")
                    },
                    label = {
                        Text(
                            text = "Alarmas",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_alarms")
                )

                NavigationBarItem(
                    selected = selectedMainTab == 1,
                    onClick = { selectedMainTab = 1 },
                    icon = {
                        if (timerState.isRunning) {
                            BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("1") } }) {
                                Icon(imageVector = Icons.Default.Timer, contentDescription = "Temporizador")
                            }
                        } else {
                            Icon(imageVector = Icons.Default.Timer, contentDescription = "Temporizador")
                        }
                    },
                    label = {
                        Text(
                            text = "Temporizador",
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_timer")
                )
            }
        },
        floatingActionButton = {
            if (selectedMainTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.openCreateDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("fab_add_alarm")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Nuevo Aviso")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nuevo Aviso", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Active Ringing Alarm Banner
            ActiveAlarmBanner(
                ringingState = ringingState,
                onStop = { viewModel.stopActiveAlarmRinging() },
                onSnooze = { viewModel.snoozeActiveAlarm() }
            )

            if (selectedMainTab == 0) {
                // --- ALARMS & REMINDERS TAB ---
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        ClockOverviewHeader(
                            currentTimeMillis = currentTimeMillis,
                            nextUpcomingAlarm = nextUpcoming,
                            onVoiceClick = { viewModel.openVoiceAssistant() }
                        )
                    }

                    // Section Title & Filter Tabs
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Avisos Programados",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        ScrollableTabRow(
                            selectedTabIndex = currentFilter.ordinal,
                            containerColor = Color.Transparent,
                            edgePadding = 0.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                if (currentFilter.ordinal < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[currentFilter.ordinal]),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        ) {
                            AlarmFilter.values().forEach { filter ->
                                val label = when (filter) {
                                    AlarmFilter.ALL -> "Todos (${allAlarms.size})"
                                    AlarmFilter.ALARMS -> "Alarmas"
                                    AlarmFilter.REMINDERS -> "Avisos"
                                    AlarmFilter.CALENDAR -> "Calendario"
                                }
                                Tab(
                                    selected = currentFilter == filter,
                                    onClick = { viewModel.setFilter(filter) },
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if (currentFilter == filter) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    },
                                    modifier = Modifier.testTag("filter_tab_${filter.name}")
                                )
                            }
                        }
                    }

                    // Alarm Cards List
                    if (filteredAlarms.isEmpty()) {
                        item {
                            EmptyAlarmsView(
                                filter = currentFilter,
                                onAddClick = { viewModel.openCreateDialog() },
                                onVoiceClick = { viewModel.openVoiceAssistant() }
                            )
                        }
                    } else {
                        items(
                            items = filteredAlarms,
                            key = { it.id }
                        ) { alarm ->
                            val isThisSpeaking = isCurrentlySpeaking && currentSpokenText == alarm.spokenMessage
                            AlarmCard(
                                alarm = alarm,
                                isCurrentlySpeaking = isThisSpeaking,
                                onToggle = { isEnabled -> viewModel.toggleAlarm(alarm, isEnabled) },
                                onEdit = { viewModel.openEditDialog(alarm) },
                                onDelete = { viewModel.deleteAlarm(alarm) },
                                onTestVoice = { viewModel.testSpokenAnnouncement(alarm.spokenMessage, alarm.repeatSpeechCount) },
                                onStopVoice = { viewModel.stopSpeaking() },
                                onOpenCalendar = if (alarm.syncToCalendar && alarm.calendarEventId != null) {
                                    {
                                        try {
                                            context.startActivity(CalendarHelper.createCalendarViewIntent(alarm.calendarEventId!!))
                                        } catch (_: Exception) {
                                            context.startActivity(CalendarHelper.createOpenCalendarIntent(alarm.nextTriggerTimeMillis))
                                        }
                                    }
                                } else null
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
                    }
                }
            } else {
                // --- TIMER TAB ---
                TimerScreen(
                    timerState = timerState,
                    presets = viewModel.timerPresets,
                    isCurrentlySpeaking = isCurrentlySpeaking,
                    onStartTimer = { totalSec, spoken, label ->
                        viewModel.startTimer(totalSec, spoken, label)
                    },
                    onPauseTimer = { viewModel.pauseTimer() },
                    onResumeTimer = { viewModel.resumeTimer() },
                    onResetTimer = { viewModel.resetTimer() },
                    onAddMinute = { viewModel.addOneMinuteToTimer() },
                    onTestVoice = { text -> viewModel.testSpokenAnnouncement(text, 1) },
                    onStopVoice = { viewModel.stopSpeaking() }
                )
            }
        }
    }

    // Voice Engine & Speakers Configuration Dialog
    if (showVoiceEngineDialog) {
        com.example.ui.components.VoiceEngineConfigDialog(
            useRealisticAIVoice = useRealisticAIVoice,
            selectedAIVoiceName = selectedAIVoiceName,
            availableAIVoices = viewModel.availableAIVoices,
            isSpeaking = isCurrentlySpeaking,
            onToggleUseAIVoice = { viewModel.setUseRealisticAIVoice(it) },
            onSelectAIVoice = { viewModel.setSelectedAIVoiceName(it) },
            onTestVoice = { text -> viewModel.testSpokenAnnouncement(text, 1) },
            onStopVoice = { viewModel.stopSpeaking() },
            onDismiss = { showVoiceEngineDialog = false }
        )
    }

    // Theme Selector Bottom Sheet
    if (showThemeDialog) {
        ThemeConfigBottomSheet(
            currentTheme = appTheme,
            onSelectTheme = { viewModel.setAppTheme(it) },
            onDismiss = { showThemeDialog = false }
        )
    }

    // Manual Creation / Edit Dialog
    if (showCreateDialog) {
        CreateEditAlarmDialog(
            initialAlarm = editingAlarm,
            isCurrentlySpeaking = isCurrentlySpeaking,
            onSave = { alarm -> viewModel.saveAlarm(alarm) },
            onDismiss = { viewModel.closeCreateEditDialog() },
            onTestVoice = { text, count -> viewModel.testSpokenAnnouncement(text, count) },
            onStopVoice = { viewModel.stopSpeaking() }
        )
    }

    // AI Voice Assistant Bottom Sheet
    if (showVoiceSheet) {
        VoiceAiBottomSheet(
            isListening = isListening,
            liveTranscript = liveTranscript,
            soundLevel = soundLevel,
            isAiParsing = isAiParsing,
            parsedResult = parsedAiResult,
            aiError = aiError,
            isCurrentlySpeaking = isCurrentlySpeaking,
            onStartListening = { viewModel.startVoiceListening() },
            onStopListening = { viewModel.stopVoiceListening() },
            onSubmitPrompt = { prompt -> viewModel.processVoiceCommandWithAi(prompt) },
            onApplyResult = { result -> viewModel.applyParsedAiResult(result) },
            onTestVoice = { text -> viewModel.testSpokenAnnouncement(text, 1) },
            onStopVoice = { viewModel.stopSpeaking() },
            onDismiss = { viewModel.closeVoiceAssistant() }
        )
    }
}

@Composable
fun EmptyAlarmsView(
    filter: AlarmFilter,
    onAddClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Sin avisos",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "No hay avisos programados",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Crea un aviso manual o utiliza la entrada por voz con IA para dictarlo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onVoiceClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Dictar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dictar por Voz")
                }

                OutlinedButton(
                    onClick = onAddClick,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Manual", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual")
                }
            }
        }
    }
}
