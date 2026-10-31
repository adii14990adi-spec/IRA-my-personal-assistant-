package com.ira.assistant.connectors

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.ira.assistant.IraApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** WhatsApp exposes only active notification previews, not its message database. */
data class WhatsAppPreview(val key: String, val sender: String, val body: String, val canReply: Boolean)

class WhatsAppService : NotificationListenerService() {
    companion object {
        private val allowedPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
        private data class ReplyTarget(val sender: String, val action: PendingIntent, val inputs: Array<RemoteInput>)
        private val lock = Any()
        private val targets = mutableMapOf<String, ReplyTarget>()
        private val _messages = MutableStateFlow<List<WhatsAppPreview>>(emptyList())
        private val _connected = MutableStateFlow(false)
        val messages: StateFlow<List<WhatsAppPreview>> = _messages.asStateFlow()
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        /** Bind the action to the selected notification AND its sender; never reply to a global "last" action. */
        fun reply(context: Context, key: String, expectedSender: String, message: String): Result<Unit> = runCatching {
            val text = message.trim()
            require(text.isNotEmpty() && text.length <= 500) { "Enter a reply of up to 500 characters." }
            synchronized(lock) {
                val target = targets[key] ?: error("That notification is no longer available for reply.")
                check(target.sender == expectedSender) { "The notification changed. Select the recipient again." }
                val results = Bundle()
                target.inputs.forEach { results.putCharSequence(it.resultKey, text) }
                val intent = Intent()
                RemoteInput.addResultsToIntent(target.inputs, intent, results)
                target.action.send(context, 0, intent)
            }
        }

        private fun clear() = synchronized(lock) {
            targets.clear()
            _messages.value = emptyList()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        clear()
        _connected.value = true
        // Existing notifications are displayed, but are never automatically replied to on reconnect.
        runCatching { activeNotifications?.forEach { remember(it, allowAutomaticReply = false) } }
    }

    override fun onListenerDisconnected() {
        _connected.value = false
        clear()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        remember(sbn, allowAutomaticReply = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(lock) {
            targets.remove(sbn.key)
            _messages.value = _messages.value.filterNot { it.key == sbn.key }
        }
    }

    private fun remember(sbn: StatusBarNotification, allowAutomaticReply: Boolean) {
        if (sbn.packageName !in allowedPackages || (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = sbn.notification.extras
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty().take(120)
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim().orEmpty().take(500)
        if (sender.isBlank() || body.isBlank() || sender.equals("WhatsApp", true)) return
        val replyAction = sbn.notification.actions?.firstOrNull { action ->
            action.actionIntent != null && action.remoteInputs?.any { it.allowFreeFormInput } == true
        }
        val inputs: Array<RemoteInput> = replyAction?.remoteInputs?.filter { it.allowFreeFormInput }?.toTypedArray() ?: emptyArray()
        val preview = WhatsAppPreview(sbn.key, sender, body, replyAction != null && inputs.isNotEmpty())
        var changed = false
        synchronized(lock) {
            val previous = _messages.value.firstOrNull { it.key == sbn.key }
            changed = previous == null || previous.sender != sender || previous.body != body
            if (replyAction != null && inputs.isNotEmpty()) targets[sbn.key] = ReplyTarget(sender, replyAction.actionIntent, inputs)
            else targets.remove(sbn.key)
            _messages.value = (listOf(preview) + _messages.value.filterNot { it.key == sbn.key }).take(20)
            // Retain actions only for visible entries.
            val visible = _messages.value.map { it.key }.toSet()
            targets.keys.retainAll(visible)
        }
        if (allowAutomaticReply && changed && preview.canReply) {
            val preferences = (application as IraApplication).preferences
            preferences.attemptAutoReply(sender) {
                reply(this, sbn.key, sender, preferences.autoReplyText).isSuccess
            }
        }
    }
}
