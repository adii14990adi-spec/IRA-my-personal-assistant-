package com.ira.assistant.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Push-to-talk only. Android's recognizer may use a network service on some devices. */
class SpeechInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun listen(language: String, onListening: (Boolean) -> Unit, onText: (String) -> Unit, onError: (String) -> Unit) {
        close()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("No speech recognizer is available on this phone.")
            return
        }
        try {
            val useOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            val instance = if (useOnDevice && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = instance
            instance.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { onListening(false) }
                override fun onError(error: Int) { onListening(false); onError("Couldn't hear that. Please try again."); close() }
                override fun onResults(results: Bundle?) {
                    onListening(false)
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) onError("No speech was recognized.") else onText(text)
                    close()
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "en_IN") "en-IN" else "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            onListening(true)
            instance.startListening(request)
        } catch (_: Exception) {
            onListening(false)
            onError("Speech recognition could not start. Check microphone permission and your speech provider.")
            close()
        }
    }

    fun close() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }
}
