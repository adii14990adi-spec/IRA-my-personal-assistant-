package com.ira.assistant.core

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** Close the underlying socket promptly when a download coroutine is cancelled. */
internal suspend fun <T> Call.executeCancellable(block: suspend (Response) -> T): T = coroutineScope {
    val call = this@executeCancellable
    val cancelWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { call.cancel() }
    }
    try {
        coroutineContext.ensureActive()
        call.execute().use { block(it) }
    } catch (failure: IOException) {
        // OkHttp reports a cancelled socket as IOException. Preserve the coroutine's
        // CancellationException so callers can distinguish cancellation from failure.
        coroutineContext.ensureActive()
        throw failure
    } finally {
        cancelWatcher.cancel()
    }
}
