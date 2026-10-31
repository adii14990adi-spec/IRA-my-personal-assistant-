package com.ira.assistant.core

import android.content.Context
import org.json.JSONObject
import java.io.File

private val supportedActions = setOf(
    "ACTION_ALARM", "ACTION_CALLER_ID", "ACTION_BATTERY", "ACTION_REMINDER",
    "ACTION_WHATSAPP", "ACTION_CALENDAR"
)

const val MAX_LANGUAGE_PACK_BYTES = 512 * 1024
val LANGUAGE_CODES = listOf("hinglish", "hi_IN", "en_IN")

data class IntentDefinition(val action: String, val keywords: List<String>)

data class LanguagePack(
    val systemPrompt: String,
    val wakeWords: List<String>,
    val intents: List<IntentDefinition>,
    val responses: Map<String, String>
) {
    fun response(key: String, vararg values: Pair<String, String>): String {
        var result = responses[key] ?: key
        values.forEach { (name, value) -> result = result.replace("{$name}", value) }
        return result
    }
}

/** Downloaded packs are data, never code. Reject unknown actions and oversized input. */
object LanguagePacks {
    fun parse(json: String): LanguagePack {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_LANGUAGE_PACK_BYTES) { "Language pack is too large." }
        // Bound nesting before JSONObject's recursive parser touches untrusted input.
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in json) {
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else when (ch) {
                '"' -> inString = true
                '{', '[' -> { depth++; require(depth <= 32) { "Language pack is nested too deeply." } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid language pack." } }
            }
        }
        require(depth == 0 && !inString) { "Invalid language pack." }
        val root = JSONObject(json)
        val prompt = root.get("system_prompt") as? String ?: throw IllegalArgumentException("system_prompt must be a string.")
        require(prompt.isNotBlank() && prompt.length <= 8000) { "Invalid system_prompt." }
        val words = root.getJSONArray("wake_words")
        require(words.length() <= 30) { "Too many wake words." }
        val wakeWords = (0 until words.length()).map { words.get(it) as? String ?: throw IllegalArgumentException("Invalid wake word.") }
        require(wakeWords.all { it.isNotBlank() && it.length <= 100 }) { "Invalid wake word." }
        val rawIntents = root.getJSONObject("daily_intents")
        require(rawIntents.length() <= 50) { "Too many intents." }
        val intents = mutableListOf<IntentDefinition>()
        val intentKeys = rawIntents.keys()
        while (intentKeys.hasNext()) {
            val entry = rawIntents.getJSONObject(intentKeys.next())
            val action = entry.get("action") as? String ?: throw IllegalArgumentException("Invalid intent action.")
            require(action in supportedActions) { "Unsupported intent action." }
            val items = entry.getJSONArray("keywords")
            require(items.length() in 1..50) { "Invalid intent keywords." }
            val keywords = (0 until items.length()).map { items.get(it) as? String ?: throw IllegalArgumentException("Invalid keyword.") }
            require(keywords.all { it.isNotBlank() && it.length <= 100 }) { "Invalid keyword." }
            intents += IntentDefinition(action, keywords)
        }
        val rawResponses = root.getJSONObject("voice_responses")
        require(rawResponses.length() <= 100) { "Too many responses." }
        val responses = mutableMapOf<String, String>()
        val keys = rawResponses.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = rawResponses.get(key) as? String ?: throw IllegalArgumentException("Invalid response.")
            require(key.length <= 80 && value.length <= 1000) { "Invalid response." }
            responses[key] = value
        }
        return LanguagePack(prompt, wakeWords, intents, responses)
    }

    fun load(context: Context, language: String): LanguagePack {
        require(language in LANGUAGE_CODES) { "Unsupported language." }
        val bundled = context.assets.open("language_packs/$language.json").bufferedReader().use { parse(it.readText()) }
        val file = File(context.filesDir, "assets/language_packs/$language.json")
        if (!file.isFile || file.length() > MAX_LANGUAGE_PACK_BYTES) return bundled
        val downloaded = runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull() ?: return bundled
        return merge(bundled, downloaded)
    }

    fun merge(bundled: LanguagePack, downloaded: LanguagePack): LanguagePack {
        // Older packs may describe only a few actions. Keep the other bundled
        // capabilities, and combine multiple downloaded entries for the same action.
        val merged = bundled.intents.associateBy { it.action }.toMutableMap()
        downloaded.intents.groupBy { it.action }.forEach { (action, definitions) ->
            merged[action] = IntentDefinition(action, definitions.flatMap { it.keywords }.distinct())
        }
        return downloaded.copy(intents = merged.values.toList(), responses = bundled.responses + downloaded.responses)
    }
}
