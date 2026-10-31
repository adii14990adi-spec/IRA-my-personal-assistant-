package com.ira.assistant.core

import java.util.Locale

sealed interface IraIntent {
    data class Alarm(val hour: Int?, val minute: Int?) : IraIntent
    data object DatedAlarm : IraIntent
    data object Battery : IraIntent
    data object Caller : IraIntent
    data object WhatsApp : IraIntent
    data class Calendar(val title: String) : IraIntent
    data object Chat : IraIntent
}

/** Routes known device intents without loading or asking the language model. */
class IntentRouter(private val intents: List<IntentDefinition>) {
    private val dateWords = Regex(
        "(?i)(?<![\\p{L}\\p{M}])(tomorrow|today|tonight|kal|aaj|parson|कल|आज|परसों|monday|tuesday|wednesday|thursday|friday|saturday|sunday|somwar|mangalwar|budhwar|guruwar|shukrawar|shanivar|ravivar|सोमवार|मंगलवार|बुधवार|गुरुवार|शुक्रवार|शनिवार|रविवार)(?![\\p{L}\\p{M}])|\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b|\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b"
    )
    private val time = Regex("(?i)(?<![\\d:])(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|baje)?(?![\\d:])")

    private fun matches(action: String, text: String): Boolean = intents
        .asSequence().filter { it.action == action }.flatMap { it.keywords.asSequence() }
        .any { keyword ->
            val pattern = "(?<![\\p{L}\\p{M}\\p{N}_])" + Regex.escape(keyword.trim()) + "(?![\\p{L}\\p{M}\\p{N}_])"
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
        }

    fun route(input: String): IraIntent {
        val text = input.trim()
        if (text.isBlank()) return IraIntent.Chat
        // Prioritize actions containing message/event text, so "remind me to charge
        // the battery" does not get mistaken for a battery status query.
        if (matches("ACTION_WHATSAPP", text)) return IraIntent.WhatsApp
        if (matches("ACTION_REMINDER", text) || matches("ACTION_CALENDAR", text)) {
            return IraIntent.Calendar(calendarTitle(text))
        }
        if (matches("ACTION_ALARM", text)) return parseAlarm(text)
        if (matches("ACTION_CALLER_ID", text)) return IraIntent.Caller
        if (matches("ACTION_BATTERY", text)) return IraIntent.Battery
        return IraIntent.Chat
    }

    private fun parseAlarm(text: String): IraIntent {
        // Android's SET_ALARM intent has no one-off date parameter. Never silently
        // turn "tomorrow" into the next occurrence, which could be today.
        if (dateWords.containsMatchIn(text)) return IraIntent.DatedAlarm
        val match = time.find(text) ?: return IraIntent.Alarm(null, null)
        var hour = match.groupValues[1].toIntOrNull() ?: return IraIntent.Alarm(null, null)
        val minute = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return IraIntent.Alarm(null, null)
        val suffix = match.groupValues[3].lowercase(Locale.ROOT).replace(".", "")
        if (minute !in 0..59 || hour !in 0..23) return IraIntent.Alarm(null, null)
        val morning = Regex("(?i)\\b(morning|subah)\\b|सुबह").containsMatchIn(text)
        val evening = Regex("(?i)\\b(evening|shaam|sham)\\b|शाम").containsMatchIn(text)
        val am = suffix == "am" || (suffix != "pm" && morning)
        val pm = suffix == "pm" || (suffix != "am" && evening)
        if (am || pm) {
            if (hour !in 1..12) return IraIntent.Alarm(null, null)
            hour = hour % 12 + if (pm) 12 else 0
        } else if (hour in 1..12 && match.groupValues[2].isEmpty()) {
            // "6 baje" alone is ambiguous; the confirmation dialog lets the user choose.
            return IraIntent.Alarm(null, null)
        }
        return IraIntent.Alarm(hour, minute)
    }

    private fun calendarTitle(text: String): String = text
        .replace(Regex("(?i)^(?:hey ira[, ]*)?(?:remind me(?: to)?|reminder(?: to)?|yaad dilana|add (?:a )?(?:calendar )?event|calendar(?: add| event)?|याद दिलाना|कैलेंडर)\\s*[:\\-]?\\s*"), "")
        .trim().take(120)
}
