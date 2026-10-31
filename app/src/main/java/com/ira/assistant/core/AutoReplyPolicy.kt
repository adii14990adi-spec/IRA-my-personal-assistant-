package com.ira.assistant.core

import java.util.Locale

/** Pure, testable safety checks for the opt-in automatic WhatsApp responder. */
object AutoReplyPolicy {
    const val COOLDOWN_MILLIS = 15 * 60_000L

    fun matches(enabled: Boolean, message: String, allowlist: String, sender: String): Boolean {
        if (!enabled || message.isBlank()) return false
        val normalized = sender.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        return allowlist.lineSequence().map { it.trim().lowercase(Locale.ROOT) }
            .any { it.isNotEmpty() && it == normalized }
    }

    fun throttled(previous: Long, now: Long): Boolean =
        previous > 0 && (now < previous || now - previous < COOLDOWN_MILLIS)
}
