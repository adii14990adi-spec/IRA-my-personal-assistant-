package com.ira.assistant.core

import android.app.ActivityManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ira.assistant.IraApplication
import com.ira.assistant.connectors.CallConnectorService
import com.ira.assistant.connectors.SystemConnector
import com.ira.assistant.connectors.WhatsAppService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private val nextMessageId = AtomicLong()
data class ChatEntry(val id: Long = nextMessageId.incrementAndGet(), val isUser: Boolean, val text: String)

data class IraUiState(
    val language: String = "hinglish",
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: TransferProgress? = null,
    val syncing: Boolean = false,
    val thinking: Boolean = false,
    val notice: String? = null,
    val messages: List<ChatEntry> = emptyList()
)

sealed interface AssistantAction {
    data class OpenAlarm(val hour: Int?, val minute: Int?) : AssistantAction
    data class OpenCalendar(val title: String) : AssistantAction
    data object OpenWhatsApp : AssistantAction
}

class IraViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IraApplication
    private val settings = app.preferences
    private val manager = app.assetsManager
    private var pack = LanguagePacks.load(app, settings.language)
    private val history = mutableListOf<LlamaBridge.Message>()
    private val _state = MutableStateFlow(IraUiState(language = settings.language, modelReady = manager.hasModel()))
    val state = _state.asStateFlow()
    private val _actions = MutableSharedFlow<AssistantAction>(extraBufferCapacity = 8)
    val actions = _actions.asSharedFlow()
    private var generationJob: Job? = null
    private var downloadJob: Job? = null

    fun clearNotice() = _state.update { it.copy(notice = null) }
    fun showNotice(text: String) = _state.update { it.copy(notice = text) }

    fun setLanguage(language: String) {
        if (language !in LANGUAGE_CODES) return
        if (_state.value.thinking) { showNotice("Stop the current response before changing language."); return }
        settings.language = language
        pack = LanguagePacks.load(app, language)
        history.clear()
        _state.update { it.copy(language = language, notice = "Language changed. A new conversation will use this language.") }
    }

    fun submit(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _state.value.thinking) return
        if (text.length > 4000) { showNotice("Please keep your message under 4,000 characters."); return }
        _state.update { it.copy(messages = it.messages + ChatEntry(isUser = true, text = text)) }
        when (val action = IntentRouter(pack.intents).route(text)) {
            is IraIntent.Alarm -> {
                reply("Let's set an alarm. Please confirm the time in the clock.")
                _actions.tryEmit(AssistantAction.OpenAlarm(action.hour, action.minute))
            }
            IraIntent.DatedAlarm -> reply("Android's alarm shortcut cannot set a specific date. Please choose the date in your clock app.")
            IraIntent.Battery -> replyBatteryStatus()
            IraIntent.Caller -> {
                val recent = CallConnectorService.recent.value
                if (recent == null || System.currentTimeMillis() - recent.timestamp !in 0L..60_000L) reply(pack.response("no_call"))
                else reply("Most recent incoming call: ${recent.displayName ?: "unknown caller"}. Call status may have changed.")
            }
            IraIntent.WhatsApp -> {
                val previews = WhatsAppService.messages.value
                if (!WhatsAppService.connected.value) reply("Allow IRA notification access in Settings to see WhatsApp previews.")
                else if (previews.isEmpty()) reply(pack.response("no_whatsapp"))
                else reply(previews.take(3).joinToString("\n") { "${it.sender}: ${it.body}" })
                _actions.tryEmit(AssistantAction.OpenWhatsApp)
            }
            is IraIntent.Calendar -> {
                reply(pack.response("calendar_prompt"))
                _actions.tryEmit(AssistantAction.OpenCalendar(action.title))
            }
            IraIntent.Chat -> chat(text)
        }
    }

    private fun reply(text: String) {
        _state.update { it.copy(messages = it.messages + ChatEntry(isUser = false, text = text)) }
        if (settings.speakReplies) app.speech.speak(text, settings.language)
    }

    fun checkBattery() {
        if (_state.value.thinking) { showNotice("Wait for the current response or stop it first."); return }
        _state.update { it.copy(messages = it.messages + ChatEntry(isUser = true, text = "Battery status")) }
        replyBatteryStatus()
    }

    private fun replyBatteryStatus() {
        val battery = SystemConnector(app).batteryStatus()
        if (battery == null) reply("Battery details aren't available right now.")
        else reply(pack.response("battery_status", "percent" to battery.percentage.toString(),
            "status" to pack.response(if (battery.charging) "charging" else "discharging")))
    }

    private fun chat(text: String) {
        if (_state.value.syncing) { reply("Please wait for language-pack sync to finish before starting a conversation."); return }
        if (!manager.hasModel()) { reply(pack.response("model_missing")); return }
        val activityManager = app.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val file = manager.modelFile()
        if (!app.llama.isLoaded() && memory.availMem < file.length() + 512L * 1024 * 1024) {
            reply("There isn't enough available memory to load the model safely. Close other apps and try again.")
            return
        }
        val placeholder = ChatEntry(isUser = false, text = "")
        _state.update { it.copy(thinking = true, messages = it.messages + placeholder) }
        generationJob = viewModelScope.launch {
            try {
                app.llama.load(file)
                val modelMessages = listOf(LlamaBridge.Message("system", pack.systemPrompt)) +
                    history.takeLast(8) + LlamaBridge.Message("user", text)
                val answer = app.llama.generate(modelMessages) { partial ->
                    _state.update { state -> state.copy(messages = state.messages.map {
                        if (it.id == placeholder.id) it.copy(text = partial) else it
                    }) }
                }
                check(answer.isNotBlank()) { "The model returned an empty reply. Please try again." }
                history += LlamaBridge.Message("user", text)
                history += LlamaBridge.Message("assistant", answer)
                while (history.size > 8) history.removeAt(0)
                _state.update { state -> state.copy(messages = state.messages.map {
                    if (it.id == placeholder.id) it.copy(text = answer) else it
                }) }
                if (settings.speakReplies) app.speech.speak(answer, settings.language)
            } catch (_: CancellationException) {
                updateReply(placeholder.id, "Stopped.")
                // Loading may have completed just as cancellation arrived. Always
                // release its native handle before leaving the cancelled coroutine.
                withContext(NonCancellable) { app.llama.unload() }
            } catch (failure: Exception) {
                updateReply(placeholder.id, failure.message ?: "Sorry, the local model couldn't answer.")
                app.llama.cancel()
                app.llama.unload()
            } finally {
                _state.update { it.copy(thinking = false) }
            }
        }
    }

    private fun updateReply(id: Long, text: String) = _state.update { state ->
        state.copy(messages = state.messages.map { if (it.id == id) it.copy(text = text) else it })
    }

    fun stopGeneration() {
        generationJob?.cancel()
        app.llama.cancel()
    }

    fun newConversation() {
        if (_state.value.thinking) return
        history.clear()
        _state.update { it.copy(messages = emptyList()) }
    }

    fun downloadModel(token: String) {
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true, downloadProgress = TransferProgress(), notice = null) }
        downloadJob = viewModelScope.launch {
            try {
                manager.downloadModel(token) { progress -> _state.update { it.copy(downloadProgress = progress) } }
                _state.update { it.copy(modelReady = manager.hasModel(), notice = "Model downloaded. IRA can now chat on your device.") }
            } catch (_: CancellationException) {
                showNotice("Model download cancelled. The partial file was removed.")
            } catch (failure: Exception) {
                showNotice(failure.message ?: "Model download failed. Check your connection and access.")
            } finally {
                _state.update { it.copy(downloading = false, downloadProgress = null) }
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }

    fun deleteModel() {
        if (_state.value.downloading || _state.value.thinking) return
        viewModelScope.launch {
            app.llama.cancel()
            app.llama.unload()
            val deleted = runCatching { val file = manager.modelFile(); !file.exists() || file.delete() }.getOrDefault(false)
            _state.update { it.copy(modelReady = manager.hasModel(), notice = if (deleted) "Model removed from this device." else "Could not remove the model.") }
        }
    }

    fun syncPacks(url: String) {
        if (_state.value.syncing) return
        if (_state.value.thinking) { showNotice("Stop the current response before syncing language packs."); return }
        _state.update { it.copy(syncing = true, notice = null) }
        viewModelScope.launch {
            try {
                val result = manager.syncLanguagePacks(url)
                settings.packUrl = url.trim()
                pack = LanguagePacks.load(app, settings.language)
                if (result.updated.isNotEmpty()) history.clear()
                val message = if (result.failed.isEmpty()) "All three language packs were updated."
                else "Updated ${result.updated.size} pack(s); could not update ${result.failed.joinToString()}. Existing packs were kept."
                showNotice(message)
            } catch (failure: Exception) {
                showNotice(failure.message ?: "Language-pack sync failed.")
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    override fun onCleared() {
        generationJob?.cancel()
        app.llama.cancel()
        super.onCleared()
    }
}
