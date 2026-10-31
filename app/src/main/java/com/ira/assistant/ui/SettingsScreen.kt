package com.ira.assistant.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ira.assistant.IraApplication
import com.ira.assistant.connectors.WhatsAppService
import com.ira.assistant.core.IraForegroundService
import com.ira.assistant.core.IraUiState
import com.ira.assistant.core.IraViewModel

@Composable
fun SettingsScreen(app: IraApplication, state: IraUiState, viewModel: IraViewModel) {
    val context = LocalContext.current
    val prefs = app.preferences
    val notificationConnected by WhatsAppService.connected.collectAsState()
    val sessionRunning by IraForegroundService.running.collectAsState()
    val sessionError by IraForegroundService.error.collectAsState()
    var token by remember { mutableStateOf("") } // Intentionally not saveable or persisted.
    var url by remember { mutableStateOf(prefs.packUrl) }
    var speakReplies by remember { mutableStateOf(prefs.speakReplies) }
    var announceCalls by remember { mutableStateOf(prefs.announceCalls) }
    var autoEnabled by remember { mutableStateOf(prefs.autoReplyEnabled) }
    var autoText by remember { mutableStateOf(prefs.autoReplyText) }
    var allowlist by remember { mutableStateOf(prefs.autoReplyAllowlist) }
    var confirmAuto by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var roleHeld by remember { mutableStateOf(roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) }
    var contactsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val callRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        viewModel.showNotice(if (roleHeld) "IRA can screen calls. Enable caller announcements below if you want them spoken." else "Call screening role was not granted.")
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted
        viewModel.showNotice(if (granted) "Contact names can now be used for caller announcements." else "Caller announcements will not look up contact names.")
    }
    fun startSession() {
        runCatching { ContextCompat.startForegroundService(context, Intent(context, IraForegroundService::class.java)) }
            .onFailure { viewModel.showNotice("Android couldn't start the session. Try again while IRA is open.") }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSession() else viewModel.showNotice("Notification permission is needed to show the active session.")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp)) {
        SectionHeader("Make IRA yours", "SETTINGS")
        Spacer(Modifier.height(8.dp))
        Text("A few thoughtful choices, all in your hands.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))

        SettingsCard("01", "The local brain", "Your conversation model stays on this device.") {
            Text(if (state.modelReady) "Model ready" else "Llama 3.2 · 3B · Q4_K_M", color = Cream, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(if (state.modelReady) "Installed in IRA's app-specific storage." else "About 2 GB to download. Wi-Fi and free storage recommended.", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (!state.modelReady) {
                Spacer(Modifier.height(13.dp))
                OutlinedTextField(value = token, onValueChange = { if (it.length <= 300) token = it },
                    label = { Text("Hugging Face token (optional)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    enabled = !state.downloading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Spacer(Modifier.height(6.dp))
                Text("Used only for this download; never saved. Gated models may require accepted license terms.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.downloading) {
                Spacer(Modifier.height(14.dp))
                val progress = state.downloadProgress
                if (progress?.percent != null) {
                    LinearProgressIndicator(progress = { progress.percent!! / 100f }, modifier = Modifier.fillMaxWidth(), color = Rose)
                    Text("${progress.percent}% · ${progress.bytes / (1024 * 1024)} MB downloaded", color = Muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Rose)
                    Text("${(progress?.bytes ?: 0) / (1024 * 1024)} MB downloaded", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = viewModel::cancelDownload) { Text("Cancel download") }
            } else if (!state.modelReady) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { val submitted = token; token = ""; viewModel.downloadModel(submitted) }, modifier = Modifier.fillMaxWidth()) { Text("Download model") }
            } else {
                TextButton(onClick = { confirmDelete = true }, enabled = !state.thinking) { Text("Remove model") }
            }
            Spacer(Modifier.height(6.dp))
            Text("Memory is tuned conservatively, but Android cannot guarantee a hard 4 GB physical RAM cap or reserve RAM for other apps.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(13.dp))

        SettingsCard("02", "Language & voice", "Choose how IRA speaks with you.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("hinglish" to "Hinglish", "hi_IN" to "हिंदी", "en_IN" to "English").forEach { (code, label) ->
                    FilterChip(selected = state.language == code, onClick = { viewModel.setLanguage(code) }, enabled = !state.thinking, label = { Text(label) })
                }
            }
            SettingSwitch("Speak replies", "Uses your Android text-to-speech voice; replies may be heard by others.", speakReplies) {
                speakReplies = it; prefs.speakReplies = it
            }
            Text("Push-to-talk uses Android's speech provider. On-device recognition is preferred but may not be available; some providers use the network.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(url, { if (it.length <= 500) url = it }, label = { Text("Language-pack base URL") },
                placeholder = { Text("https://example.com/ira/packs") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                enabled = !state.syncing, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            Spacer(Modifier.height(6.dp))
            Text("Optional trusted HTTPS endpoint serving hinglish.json, hi_IN.json and en_IN.json. Bundled packs work offline.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(9.dp))
            OutlinedButton(onClick = { viewModel.syncPacks(url) }, enabled = url.isNotBlank() && !state.syncing && !state.thinking, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.syncing) "Syncing…" else "Sync language packs")
            }
        }
        Spacer(Modifier.height(13.dp))

        SettingsCard("03", "Device connections", "You choose what IRA can access.") {
            Text("WhatsApp notifications · ${if (notificationConnected) "connected" else "not connected"}", color = Cream, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Android grants IRA access to all notifications; IRA filters locally for WhatsApp.", color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    .onFailure { viewModel.showNotice("Open Android's notification-access settings to enable IRA.") }
            }) { Text("Open notification access") }
            Spacer(Modifier.height(8.dp))
            Text("Call screening role · ${if (roleHeld) "granted" else "not granted"}", color = Cream, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Android allows one screening app. IRA always permits calls; availability varies by device.", color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching {
                    check(roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) { "Call screening isn't available on this phone." }
                    callRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }.onFailure { viewModel.showNotice(it.message ?: "Call screening isn't available.") }
            }) { Text(if (roleHeld) "Review call role" else "Enable call screening") }
            TextButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                Text(if (contactsGranted) "Contact access granted" else "Allow contact-name lookup")
            }
            SettingSwitch("Announce incoming callers", "Caller names are spoken aloud when available.", announceCalls) {
                announceCalls = it; prefs.announceCalls = it
            }
            Spacer(Modifier.height(6.dp))
            Text("Optional active session · ${if (sessionRunning) "running" else "off"}", color = Cream, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("A user-started foreground notification keeps the session visible. Android can still stop background work; the microphone is not kept open.", color = Muted, style = MaterialTheme.typography.bodySmall)
            sessionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = {
                if (sessionRunning) context.stopService(Intent(context, IraForegroundService::class.java))
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else startSession()
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text(if (sessionRunning) "Stop session" else "Start visible session") }
        }
        Spacer(Modifier.height(13.dp))

        SettingsCard("04", "Automatic WhatsApp replies", "Off by default · only for names you explicitly allow.") {
            Text("Use exact notification titles, one per line. Groups can match too. A 15-minute cooldown applies per title, even if a reply attempt fails.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = autoText, onValueChange = {
                if (it.length <= 500) { autoEnabled = false; prefs.autoReplyEnabled = false; autoText = it; prefs.autoReplyText = it }
            }, label = { Text("Automatic reply message") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(value = allowlist, onValueChange = {
                if (it.length <= 4000) { autoEnabled = false; prefs.autoReplyEnabled = false; allowlist = it; prefs.autoReplyAllowlist = it }
            }, label = { Text("Allowed notification titles") }, placeholder = { Text("e.g. Aditi\nMum") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            SettingSwitch("Enable automatic replies", "Editing the message or list turns this off again.", autoEnabled) { checked ->
                if (!checked) { autoEnabled = false; prefs.autoReplyEnabled = false }
                else if (autoText.isBlank() || allowlist.lineSequence().none { it.isNotBlank() }) viewModel.showNotice("Enter a reply and at least one exact notification title first.")
                else if (!notificationConnected) viewModel.showNotice("Connect notification access before enabling automatic replies.")
                else confirmAuto = true
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Made for the little things.  ✦", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
    }
    if (confirmAuto) AlertDialog(onDismissRequest = { confirmAuto = false }, title = { Text("Turn on automatic replies?") },
        text = { Text("IRA will send your saved message without asking each time, but only to active WhatsApp notifications whose title exactly matches your list. Group titles can match. You can turn this off here at any time.") },
        confirmButton = { Button(onClick = { prefs.autoReplyEnabled = true; autoEnabled = true; confirmAuto = false }) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = { confirmAuto = false }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Remove the local model?") },
        text = { Text("This frees its storage. Download it again whenever you'd like to chat.") },
        confirmButton = { Button(onClick = { confirmDelete = false; viewModel.deleteModel() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } })
}

@Composable
private fun SettingsCard(number: String, title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(23.dp), color = Surface, border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(number, color = Rose, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(title, color = Cream, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Cream, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
