package com.ira.assistant.core

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

/** Only settings are persisted. HF tokens, notification contents and chats are not. */
class IraPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("ira_settings", Context.MODE_PRIVATE)
    private val replyLock = Any()

    var language: String
        get() = preferences.getString("language", "hinglish")?.takeIf { it in LANGUAGE_CODES } ?: "hinglish"
        set(value) { require(value in LANGUAGE_CODES); preferences.edit().putString("language", value).apply() }
    var packUrl: String
        get() = preferences.getString("pack_url", "") ?: ""
        set(value) { preferences.edit().putString("pack_url", value).apply() }
    var speakReplies: Boolean
        get() = preferences.getBoolean("speak_replies", false)
        set(value) { preferences.edit().putBoolean("speak_replies", value).apply() }
    var announceCalls: Boolean
        get() = preferences.getBoolean("announce_calls", false)
        set(value) { preferences.edit().putBoolean("announce_calls", value).apply() }
    var autoReplyEnabled: Boolean
        get() = preferences.getBoolean("auto_reply_enabled", false)
        set(value) { preferences.edit().putBoolean("auto_reply_enabled", value).apply() }
    var autoReplyText: String
        get() = preferences.getString("auto_reply_text", "") ?: ""
        set(value) { preferences.edit().putString("auto_reply_text", value.take(500)).apply() }
    var autoReplyAllowlist: String
        get() = preferences.getString("auto_reply_allowlist", "") ?: ""
        set(value) { preferences.edit().putString("auto_reply_allowlist", value.take(4000)).apply() }

    fun mayAutoReply(sender: String): Boolean {
        return AutoReplyPolicy.matches(autoReplyEnabled, autoReplyText, autoReplyAllowlist, sender)
    }

    /** Persist a best-effort 15-minute per-title cooldown, even across process restarts. */
    fun attemptAutoReply(sender: String, now: Long = System.currentTimeMillis(), send: () -> Boolean): Boolean =
        synchronized(replyLock) {
            if (!mayAutoReply(sender)) return@synchronized false
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(sender.trim().lowercase(Locale.ROOT).toByteArray())
                .joinToString("") { "%02x".format(it) }
            val key = "reply_$hash"
            val previous = preferences.getLong(key, 0L)
            if (AutoReplyPolicy.throttled(previous, now)) return@synchronized false
            // Persist the cooldown before submitting: a process death immediately after
            // send must not cause another automatic reply when the listener reconnects.
            // A failed submission still consumes the cooldown; manual reply remains available.
            if (!preferences.edit().putLong(key, now).commit()) return@synchronized false
            send()
        }
}
