package com.ira.assistant.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Android's TTS voice and offline availability are selected by the user/device. */
class SpeechOutput(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pending: Pair<String, String>? = null

    init {
        main.post {
            if (!closed) {
                engine = TextToSpeech(context.applicationContext) { status ->
                    ready = status == TextToSpeech.SUCCESS
                    val next = pending
                    pending = null
                    if (ready && next != null) speakOnMain(next.first, next.second)
                }
            }
        }
    }

    fun speak(text: String, language: String) {
        if (text.isBlank()) return
        main.post {
            if (!closed) {
                if (ready) speakOnMain(text, language) else pending = text to language
            }
        }
    }

    private fun speakOnMain(text: String, language: String) {
        val selected = if (language == "en_IN") Locale.forLanguageTag("en-IN") else Locale.forLanguageTag("hi-IN")
        val tts = engine ?: return
        if (tts.isLanguageAvailable(selected) >= TextToSpeech.LANG_AVAILABLE) tts.language = selected
        tts.speak(text.take(1500), TextToSpeech.QUEUE_FLUSH, null, "ira-${System.nanoTime()}")
    }

    fun close() {
        main.post {
            closed = true
            pending = null
            engine?.stop()
            engine?.shutdown()
            engine = null
        }
    }
}
