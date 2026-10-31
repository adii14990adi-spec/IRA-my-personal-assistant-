package com.ira.assistant

import android.app.Application
import android.content.ComponentCallbacks2
import com.ira.assistant.core.AssetSyncManager
import com.ira.assistant.core.IraPreferences
import com.ira.assistant.core.LlamaBridge
import com.ira.assistant.core.SpeechOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IraApplication : Application() {
    val preferences by lazy { IraPreferences(this) }
    val assetsManager by lazy { AssetSyncManager(this) }
    val llama by lazy { LlamaBridge() }
    val speech by lazy { SpeechOutput(this) }
    // Screening services can be unbound immediately after allowing a call, so
    // the short contact lookup / announcement outlives the service instance.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val memoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            llama.cancel()
            memoryScope.launch { llama.unload() }
        }
    }
}
