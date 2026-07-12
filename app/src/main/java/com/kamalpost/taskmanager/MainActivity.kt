package com.kamalpost.taskmanager

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kamalpost.taskmanager.platform.AutoBackup
import com.kamalpost.taskmanager.platform.Notifications
import com.kamalpost.taskmanager.platform.ReminderScheduler
import com.kamalpost.taskmanager.platform.TimerService
import com.kamalpost.taskmanager.ui.screens.DetailsScreen
import com.kamalpost.taskmanager.ui.screens.ManageScreen
import com.kamalpost.taskmanager.ui.screens.TasksScreen
import com.kamalpost.taskmanager.ui.screens.TimerScreen
import com.kamalpost.taskmanager.ui.theme.AccentBlue
import com.kamalpost.taskmanager.ui.theme.AccentGreen
import com.kamalpost.taskmanager.ui.theme.AccentRed
import com.kamalpost.taskmanager.ui.theme.AccentYellow
import com.kamalpost.taskmanager.ui.theme.Bg
import com.kamalpost.taskmanager.ui.theme.Surface
import com.kamalpost.taskmanager.ui.theme.Surface2
import com.kamalpost.taskmanager.ui.theme.TaskManagerTheme
import com.kamalpost.taskmanager.ui.theme.TextMuted
import com.kamalpost.taskmanager.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChangedBy
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannels(this)
        setContent {
            TaskManagerTheme {
                TaskManagerApp()
            }
        }
    }
}

private enum class Tab(val label: String) {
    Tasks("Tasks"),
    Timer("Timer"),
    Manage("Manage")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskManagerApp(vm: TaskViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(Tab.Tasks.ordinal) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var autoBackupOn by remember { mutableStateOf(AutoBackup.folder(context) != null) }

    // Wire the ViewModel's platform hooks: reminders + auto-backup
    LaunchedEffect(vm) {
        val appContext = context.applicationContext
        vm.hooks = PlatformHooks(
            onDataChanged = { json -> AutoBackup.write(appContext, json) },
            onTaskScheduleChanged = { task -> ReminderScheduler.sync(appContext, task) },
            onAllRescheduled = { tasks -> ReminderScheduler.syncAll(appContext, tasks) }
        )
    }

    // Ask for notification permission (Android 13+) once at startup
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Keep the pomodoro alive in the background via the foreground service
    LaunchedEffect(Unit) {
        TimerEngine.state
            .distinctUntilChangedBy { it.running }
            .collect { st ->
                if (st.running) TimerService.start(context.applicationContext)
            }
    }

    // --- Toasts -> Snackbar (with optional action, e.g. UNDO) ---
    val snackbarHostState = remember { SnackbarHostState() }
    var toastType by remember { mutableStateOf(ToastType.Info) }
    LaunchedEffect(Unit) {
        vm.toasts.collect { t ->
            toastType = t.type
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = t.text,
                actionLabel = t.actionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) t.action?.invoke()
        }
    }

    // --- SAF: export / import backups + auto-backup folder ---
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(vm.exportJson().toByteArray())
                }
            }.onSuccess { vm.notifyExported() }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().decodeToString()
                }
            }.getOrNull()?.let { vm.importBackup(it) }
        }
    }
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            AutoBackup.setFolder(context, uri)
            AutoBackup.write(context.applicationContext, vm.exportJson())
            autoBackupOn = true
            vm.notifyBackupFolderSet()
        }
    }

    // Details is a pushed screen: system back returns to the list
    if (detailsOpen && state.selectedTask != null) {
        BackHandler { detailsOpen = false }
        DetailsScreen(
            state = state,
            vm = vm,
            onBack = { detailsOpen = false }
        )
        // Snackbars still need a host while details is open
        Box(modifier = Modifier.fillMaxSize()) {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) { data ->
                ToastSnackbar(data = data, type = toastType)
            }
        }
        return
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot()
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Task Manager",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    // Compact running-timer chip; tap to open the Timer screen
                    val timerActive = timer.running ||
                        timer.secondsLeft != timer.presetMinutes * 60
                    if (timerActive && tab != Tab.Timer.ordinal) {
                        val chipColor = when {
                            timer.secondsLeft <= 60 -> AccentRed
                            timer.secondsLeft <= 5 * 60 -> AccentYellow
                            else -> AccentGreen
                        }
                        AssistChip(
                            onClick = { tab = Tab.Timer.ordinal },
                            label = {
                                Text(
                                    "%02d:%02d".format(
                                        timer.secondsLeft / 60, timer.secondsLeft % 60
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = chipColor
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Timer,
                                    contentDescription = "Timer",
                                    tint = chipColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = chipColor.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    LiveClock()
                    Spacer(Modifier.width(16.dp))
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = {
                            val icon = when (t) {
                                Tab.Tasks -> Icons.Filled.Checklist
                                Tab.Timer -> Icons.Filled.Timer
                                Tab.Manage -> Icons.Filled.Tune
                            }
                            if (t == Tab.Tasks) {
                                val overdue = state.tasks.count {
                                    !it.completed && it.dueAt.isNotEmpty() && isPast(it.dueAt)
                                }
                                BadgedBox(badge = {
                                    if (overdue > 0) {
                                        Badge(containerColor = AccentRed) {
                                            Text("$overdue", fontSize = 10.sp)
                                        }
                                    }
                                }) {
                                    Icon(icon, contentDescription = t.label)
                                }
                            } else {
                                Icon(icon, contentDescription = t.label)
                            }
                        },
                        label = {
                            Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AccentBlue,
                            selectedTextColor = AccentBlue,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = AccentBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                ToastSnackbar(data = data, type = toastType)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Bg)
        ) {
            when (Tab.entries[tab]) {
                Tab.Tasks -> TasksScreen(
                    state = state,
                    vm = vm,
                    onOpenDetails = { detailsOpen = true }
                )
                Tab.Timer -> TimerScreen(
                    timer = timer,
                    onToggle = vm::toggleTimer,
                    onPreset = vm::setTimerPreset
                )
                Tab.Manage -> ManageScreen(
                    state = state,
                    vm = vm,
                    onExport = {
                        val stamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
                        exportLauncher.launch("tasks_export_$stamp.json")
                    },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                    autoBackupOn = autoBackupOn,
                    onChooseBackupFolder = { backupFolderLauncher.launch(null) },
                    onDisableAutoBackup = {
                        AutoBackup.clearFolder(context)
                        autoBackupOn = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ToastSnackbar(
    data: androidx.compose.material3.SnackbarData,
    type: ToastType
) {
    val color = when (type) {
        ToastType.Success -> AccentGreen
        ToastType.Error -> AccentRed
        ToastType.Warning -> AccentYellow
        ToastType.Info -> AccentBlue
    }
    Snackbar(
        snackbarData = data,
        containerColor = Surface2,
        contentColor = color,
        actionColor = AccentBlue
    )
}

/** Pulsing green status dot, a nod to the web app's .status-dot. */
@Composable
private fun StatusDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(AccentGreen, CircleShape)
    )
}

/** Live HH:mm:ss clock; red phase during the second half of each hour, like the web app. */
@Composable
private fun LiveClock() {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }
    val redPhase = now.minute >= 30
    Text(
        text = now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
        color = if (redPhase) AccentRed else AccentGreen,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}
