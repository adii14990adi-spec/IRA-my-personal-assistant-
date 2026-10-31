package com.ira.assistant.connectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import com.ira.assistant.IraApplication
import com.ira.assistant.core.LanguagePacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentCaller(val displayName: String?, val timestamp: Long)

class CallConnectorService : CallScreeningService() {
    companion object {
        private val _recent = MutableStateFlow<RecentCaller?>(null)
        val recent: StateFlow<RecentCaller?> = _recent.asStateFlow()

        private fun findContact(context: Context, number: String): String? {
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
            return runCatching {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.take(120) else null
                }
            }.getOrNull()
        }
    }

    override fun onScreenCall(details: Call.Details) {
        // Always allow the call immediately; Android requires a response within five seconds.
        runCatching { respondToCall(details, CallResponse.Builder().build()) }
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val app = application as IraApplication
        val prefs = app.preferences
        val number = details.handle?.schemeSpecificPart
        val observedAt = System.currentTimeMillis()
        // Android may unbind this service immediately after respondToCall. Use the
        // application scope and TTS, not a scope that is cancelled in onDestroy.
        app.appScope.launch {
            val name = withContext(Dispatchers.IO) { number?.let { findContact(app, it) } }
            _recent.value = RecentCaller(name, observedAt)
            val age = System.currentTimeMillis() - observedAt
            if (prefs.announceCalls && age in 0L..15_000L) {
                val pack = withContext(Dispatchers.IO) { runCatching { LanguagePacks.load(app, prefs.language) }.getOrNull() }
                val text = if (name.isNullOrBlank()) pack?.response("unknown_call") ?: "An unknown number is calling."
                else pack?.response("incoming_call", "caller" to name) ?: "$name is calling."
                app.speech.speak(text, prefs.language)
            }
        }
    }
}
