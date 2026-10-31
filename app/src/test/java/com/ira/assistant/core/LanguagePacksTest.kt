package com.ira.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanguagePacksTest {
    private val valid = """{
        "system_prompt":"You are IRA.",
        "wake_words":["hey ira"],
        "daily_intents":{"battery":{"keywords":["battery"],"action":"ACTION_BATTERY"}},
        "voice_responses":{"incoming_call":"Calling: {caller}"}
    }""".trimIndent()

    @Test fun validPackIsDataAndTemplatesReplaceOnlyNamedValues() {
        val pack = LanguagePacks.parse(valid)
        assertEquals("ACTION_BATTERY", pack.intents.single().action)
        assertEquals("Calling: Aditi", pack.response("incoming_call", "caller" to "Aditi"))
    }

    @Test fun unknownActionsAndNonStringPromptAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { LanguagePacks.parse(valid.replace("ACTION_BATTERY", "RUN_SHELL")) }
        assertThrows(IllegalArgumentException::class.java) { LanguagePacks.parse(valid.replace("\"You are IRA.\"", "123")) }
    }

    @Test fun oversizedAndDeeplyNestedPacksAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { LanguagePacks.parse(" ".repeat(MAX_LANGUAGE_PACK_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { LanguagePacks.parse("[".repeat(33) + "]".repeat(33)) }
    }

    @Test fun downloadedPackKeepsMissingActionsAndCombinesSynonyms() {
        val bundled = LanguagePacks.parse(valid).copy(intents = listOf(
            IntentDefinition("ACTION_ALARM", listOf("alarm")), IntentDefinition("ACTION_BATTERY", listOf("battery"))))
        val downloaded = LanguagePacks.parse(valid).copy(intents = listOf(
            IntentDefinition("ACTION_ALARM", listOf("wake me")), IntentDefinition("ACTION_ALARM", listOf("jagao"))))
        val merged = LanguagePacks.merge(bundled, downloaded)
        assertEquals(listOf("wake me", "jagao"), merged.intents.first { it.action == "ACTION_ALARM" }.keywords)
        assertEquals(listOf("battery"), merged.intents.first { it.action == "ACTION_BATTERY" }.keywords)
    }
}
