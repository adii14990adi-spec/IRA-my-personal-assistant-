package com.ira.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ira.assistant.connectors.WhatsAppPreview
import com.ira.assistant.connectors.WhatsAppService

@Composable
fun WhatsAppScreen(onNotice: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val messages by WhatsAppService.messages.collectAsState()
    val connected by WhatsAppService.connected.collectAsState()
    var replyingTo by remember { mutableStateOf<WhatsAppPreview?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp)) {
        SectionHeader("Your messages", "WHATSAPP")
        Spacer(Modifier.height(9.dp))
        Text("Only currently visible WhatsApp notification previews appear here. IRA never reads your chat database.",
            color = Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))
        if (!connected) {
            Surface(color = Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Outline)) {
                Column(Modifier.fillMaxWidth().padding(19.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = Lavender)
                    Spacer(Modifier.height(13.dp))
                    Text("Connect notifications", color = Cream, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text("Android requires you to explicitly grant notification access before IRA can show or reply to previews.", color = Muted)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onSettings) { Text("Go to settings") }
                }
            }
        } else if (messages.isEmpty()) {
            Surface(color = Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Outline)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("All quiet for now.", color = Cream, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("New WhatsApp notifications will appear here while they are active.", color = Muted)
                }
            }
        } else {
            messages.forEach { item ->
                Surface(color = Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Outline),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(17.dp)) {
                        Text(item.sender, color = Cream, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(5.dp))
                        Text(item.body, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (item.canReply) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { replyingTo = item }) { Text("Reply") }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Previews are kept in memory only and disappear when notifications are dismissed or IRA disconnects.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    replyingTo?.let { selected ->
        var reply by remember(selected.key) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { replyingTo = null }, title = { Text("Reply to ${selected.sender}") }, text = {
            Column {
                Text("This will submit your message through the selected WhatsApp notification.", color = Muted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(reply, { if (it.length <= 500) reply = it }, label = { Text("Your reply") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 5)
            }
        }, confirmButton = {
            Button(enabled = reply.isNotBlank(), onClick = {
                val result = WhatsAppService.reply(context, selected.key, selected.sender, reply)
                onNotice(if (result.isSuccess) "Reply submitted to WhatsApp. Delivery isn't confirmed."
                    else result.exceptionOrNull()?.message ?: "Couldn't send the reply.")
                replyingTo = null
            }) { Text("Send reply") }
        }, dismissButton = { TextButton(onClick = { replyingTo = null }) { Text("Cancel") } })
    }
}
