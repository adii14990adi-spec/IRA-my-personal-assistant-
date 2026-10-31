package com.ira.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ira.assistant.IraApplication
import com.ira.assistant.core.AssistantAction
import com.ira.assistant.core.IraIntent
import com.ira.assistant.core.IraViewModel
import com.ira.assistant.core.SpeechInput
import kotlinx.coroutines.flow.collect

@Composable
fun IraScreen(viewModel: IraViewModel, speechInput: SpeechInput) {
    val context = LocalContext.current
    val app = context.applicationContext as IraApplication
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var listening by remember { mutableStateOf(false) }
    var alarm by remember { mutableStateOf<IraIntent.Alarm?>(null) }
    var calendarTitle by remember { mutableStateOf<String?>(null) }
    var pendingCalendarTitle by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    fun beginListening() = speechInput.listen(state.language, { listening = it }, viewModel::submit, viewModel::showNotice)
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) beginListening() else viewModel.showNotice("Microphone permission is needed for push-to-talk.")
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_CALENDAR] == true && result[Manifest.permission.WRITE_CALENDAR] == true) {
            calendarTitle = pendingCalendarTitle
        } else viewModel.showNotice("Allow calendar access to choose a calendar and save an event.")
    }
    fun openCalendar(title: String) {
        pendingCalendarTitle = title
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (read && write) calendarTitle = title
        else calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    fun openMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is AssistantAction.OpenAlarm -> alarm = IraIntent.Alarm(action.hour, action.minute)
                is AssistantAction.OpenCalendar -> openCalendar(action.title)
                AssistantAction.OpenWhatsApp -> selectedTab = 1
            }
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { message ->
            snackbar.showSnackbar(message)
            viewModel.clearNotice()
        }
    }

    Scaffold(
        containerColor = Background,
        topBar = { IraTopBar() },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Background, contentColor = Cream) {
                listOf("Home", "Messages", "Settings").forEachIndexed { index, title ->
                    val icon = when (index) { 0 -> Icons.Outlined.Home; 1 -> Icons.Outlined.ChatBubbleOutline; else -> Icons.Outlined.Tune }
                    NavigationBarItem(selected = selectedTab == index, onClick = { selectedTab = index },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = RoseLight, selectedTextColor = RoseLight,
                            indicatorColor = Rose.copy(alpha = 0.15f), unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(state, listening, ::openMic, viewModel::submit, viewModel::stopGeneration,
                    viewModel::newConversation, { alarm = IraIntent.Alarm(null, null) }, viewModel::checkBattery,
                    { selectedTab = 1 }, { openCalendar("") }, { selectedTab = 2 })
                1 -> WhatsAppScreen(onNotice = viewModel::showNotice, onSettings = { selectedTab = 2 })
                else -> SettingsScreen(app, state, viewModel)
            }
        }
    }
    alarm?.let { initial ->
        AlarmDialog(initial, onDismiss = { alarm = null }, onNotice = viewModel::showNotice)
    }
    calendarTitle?.let { title ->
        CalendarDialog(title, onDismiss = { calendarTitle = null }, onNotice = viewModel::showNotice)
    }
}

@Composable
private fun IraTopBar() {
    Row(Modifier.fillMaxWidth().background(Background).statusBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Rose.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text("✦", color = Rose, fontSize = 21.sp)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text("IRA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Cream, letterSpacing = 1.6.sp)
                Text("YOUR PERSONAL ASSISTANT", color = Muted, fontSize = 8.sp, letterSpacing = 0.9.sp)
            }
        }
        Text("HERE FOR YOU  ✳", color = Rose, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
    }
}
