package com.ira.assistant.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ira.assistant.connectors.AlarmConnector
import com.ira.assistant.connectors.CalendarConnector
import com.ira.assistant.connectors.WritableCalendar
import com.ira.assistant.core.IraIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmDialog(initial: IraIntent.Alarm, onDismiss: () -> Unit, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    var hour by remember(initial) { mutableStateOf((initial.hour ?: 7).toString().padStart(2, '0')) }
    var minute by remember(initial) { mutableStateOf((initial.minute ?: 0).toString().padStart(2, '0')) }
    val valid = hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Set an alarm") }, text = {
        Column {
            Text("Choose a time in 24-hour format. Your Clock app will open so you can review and save it.", color = Muted)
            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(hour, { if (it.length <= 2 && it.all(Char::isDigit)) hour = it },
                    modifier = Modifier.weight(1f), label = { Text("Hour") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(minute, { if (it.length <= 2 && it.all(Char::isDigit)) minute = it },
                    modifier = Modifier.weight(1f), label = { Text("Minute") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }, confirmButton = {
        Button(enabled = valid, onClick = {
            val result = AlarmConnector(context).open(hour.toInt(), minute.toInt())
            onNotice(if (result.isSuccess) "Clock opened. Review and save your alarm there."
                else result.exceptionOrNull()?.message ?: "Could not open the clock.")
            onDismiss()
        }) { Text("Open clock") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun CalendarDialog(initialTitle: String, onDismiss: () -> Unit, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connector = remember { CalendarConnector(context.applicationContext) }
    var title by remember(initialTitle) { mutableStateOf(initialTitle.take(120)) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var calendars by remember { mutableStateOf<List<WritableCalendar>?>(null) }
    var selected by remember { mutableStateOf<WritableCalendar?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { connector.writableCalendars() } }
        calendars = result.getOrDefault(emptyList())
        selected = calendars?.firstOrNull()
        error = result.exceptionOrNull()?.message ?: if (calendars.isNullOrEmpty()) "No writable, visible calendar was found." else null
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Plan an event") }, text = {
        Column {
            Text("Choose a calendar and confirm. Events are one hour long.", color = Muted)
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { if (it.length <= 120) title = it }, label = { Text("Event title") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) },
                        date.year, date.monthValue - 1, date.dayOfMonth).show()
                }) { Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))) }
                TextButton(onClick = {
                    TimePickerDialog(context, { _, hour, minute -> time = LocalTime.of(hour, minute) }, time.hour, time.minute, true).show()
                }) { Text(time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            }
            if (calendars == null) Text("Finding calendars…", color = Muted)
            else if (!calendars.isNullOrEmpty()) {
                TextButton(onClick = { expanded = true }) { Text("Calendar: ${selected?.name ?: "Choose"}  ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    calendars.orEmpty().forEach { calendar ->
                        DropdownMenuItem(text = { Text(listOf(calendar.name, calendar.account).filter { it.isNotBlank() }.joinToString(" · ")) },
                            onClick = { selected = calendar; expanded = false })
                    }
                }
            }
            error?.let { Text(it, color = Rose) }
        }
    }, confirmButton = {
        Button(enabled = !saving && title.isNotBlank() && selected != null, onClick = {
            val chosen = selected ?: return@Button
            saving = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { connector.addEvent(chosen.id, title, LocalDateTime.of(date, time)) }
                saving = false
                if (result.isSuccess) {
                    onNotice("Event saved to ${chosen.name}.")
                    onDismiss()
                } else error = result.exceptionOrNull()?.message ?: "Could not save the event."
            }
        }) { Text(if (saving) "Saving…" else "Save event") }
    }, dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } })
}
