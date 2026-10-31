package com.ira.assistant.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ira.assistant.core.ChatEntry
import com.ira.assistant.core.IraUiState
import java.time.LocalTime

@Composable
fun HomeScreen(
    state: IraUiState,
    listening: Boolean,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onAlarm: () -> Unit,
    onBattery: () -> Unit,
    onWhatsApp: () -> Unit,
    onCalendar: () -> Unit,
    onSettings: () -> Unit
) {
    val scroll = rememberScrollState()
    LaunchedEffect(state.messages.size, state.thinking) {
        if (state.messages.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(22.dp))
            Text(greeting(), color = Muted, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(8.dp))
            Text("A little help,\na lot of heart.", style = MaterialTheme.typography.headlineMedium,
                lineHeight = 37.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = Cream)
            Spacer(Modifier.height(8.dp))
            Text("I'm IRA, your everyday companion.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(17.dp))
            VoiceOrb(listening, enabled = !state.thinking, onClick = onMic)
            Spacer(Modifier.height(8.dp))
            Text(if (listening) "I'M LISTENING" else if (state.thinking) "WAIT FOR RESPONSE" else "TAP TO SPEAK", color = RoseLight,
                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.8.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text("Push to talk · your mic stays off otherwise", color = Muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(22.dp))
            Surface(onClick = { if (!state.modelReady) onSettings() }, shape = RoundedCornerShape(50),
                color = if (state.modelReady) Mint.copy(alpha = 0.09f) else Rose.copy(alpha = 0.09f),
                border = BorderStroke(1.dp, if (state.modelReady) Mint.copy(alpha = 0.25f) else Rose.copy(alpha = 0.24f))) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(if (state.modelReady) Mint else Rose, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.modelReady) "ON-DEVICE MODEL READY" else "SET UP YOUR LOCAL MODEL  ↗",
                        color = if (state.modelReady) Mint else RoseLight, fontSize = 10.sp,
                        letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(30.dp))
            SectionHeader("A little shortcut", "QUICK ACTIONS")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction("Alarm", "Wake up on time", Icons.Outlined.AccessTime, Rose, Modifier.weight(1f), onAlarm)
                QuickAction("Battery", "Check your charge", Icons.Outlined.BatteryChargingFull, Mint, Modifier.weight(1f), onBattery)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction("WhatsApp", "Read & reply", Icons.Outlined.ChatBubbleOutline, Lavender, Modifier.weight(1f), onWhatsApp)
                QuickAction("Calendar", "Make a little plan", Icons.Outlined.CalendarMonth, Color(0xFFF1C5A1), Modifier.weight(1f), onCalendar)
            }
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeader("Let's talk", "CONVERSATION", Modifier.weight(1f))
                if (state.messages.isNotEmpty()) TextButton(onClick = onNewChat, enabled = !state.thinking) { Text("New chat", color = Rose) }
            }
            Spacer(Modifier.height(12.dp))
            if (state.messages.isEmpty()) {
                Surface(color = Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline)) {
                    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✦", color = Rose, fontSize = 21.sp)
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text("I'm here when you need me.", color = Cream, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(3.dp))
                            Text("Ask a question or tell me about your day.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                state.messages.forEach { MessageBubble(it) }
            }
            Spacer(Modifier.height(20.dp))
        }
        ChatComposer(state.thinking, onSend, onStop)
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "GOOD MORNING"
    in 12..16 -> "GOOD AFTERNOON"
    else -> "GOOD EVENING"
}

@Composable
fun SectionHeader(title: String, eyebrow: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(eyebrow, color = Rose, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(title, color = Cream, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VoiceOrb(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val glow by transition.animateFloat(initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (listening) 800 else 2400), RepeatMode.Reverse), label = "glow")
    Box(Modifier.size(178.dp).semantics { contentDescription = if (listening) "Listening" else "Tap to speak to IRA" }
        .clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Rose.copy(alpha = 0.20f * glow), Color.Transparent)), radius = radius)
            drawCircle(color = Rose.copy(alpha = 0.14f), radius = radius * 0.84f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            drawCircle(color = Rose.copy(alpha = 0.22f), radius = radius * 0.70f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
        Box(Modifier.size(112.dp).background(Brush.radialGradient(
            colors = listOf(Color(0xFFFFD8E2), Color(0xFFCA8AA8), Color(0xFF634762)),
            center = Offset(26f, 20f)), CircleShape).border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = Color(0xFF4C263C), modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, color = Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline)) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(37.dp).background(accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(15.dp))
            Text(title, color = Cream, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatEntry) {
    Row(Modifier.fillMaxWidth().padding(bottom = 9.dp), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = if (message.isUser) Color(0xFF453043) else Surface,
            shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (message.isUser) Color(0xFF68495F) else Outline),
            modifier = Modifier.fillMaxWidth(0.88f)) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
                Text(if (message.isUser) "YOU" else "IRA", color = if (message.isUser) RoseLight else Rose,
                    fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(message.text.ifBlank { "Thinking…" }, color = Cream, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun ChatComposer(thinking: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    var input by remember { mutableStateOf("") }
    val send = {
        if (input.isNotBlank() && !thinking) {
            onSend(input)
            input = ""
        }
    }
    Row(Modifier.fillMaxWidth().background(Background).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = input, onValueChange = { if (it.length <= 4000) input = it },
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp), maxLines = 3,
            placeholder = { Text("Ask IRA anything…", color = Muted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                focusedIndicatorColor = Rose, unfocusedIndicatorColor = Outline))
        Surface(shape = CircleShape, color = Rose, modifier = Modifier.size(52.dp)) {
            IconButton(onClick = { if (thinking) onStop() else send() }, enabled = thinking || input.isNotBlank()) {
                Icon(if (thinking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                    contentDescription = if (thinking) "Stop response" else "Send message", tint = Color(0xFF321D2B))
            }
        }
    }
}
