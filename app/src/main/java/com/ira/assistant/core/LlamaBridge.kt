package com.ira.assistant.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Owns exactly one native engine. Free/load/generate are serialized; cancel is thread-safe. */
class LlamaBridge {
    fun interface TokenSink { fun onToken(bytes: ByteArray) }
    data class Message(val role: String, val content: String)

    private val operation = Mutex()
    private val handleLock = Any()
    private var handle: Long = 0

    companion object {
        private val nativeAvailable = runCatching { System.loadLibrary("ira_llm") }.isSuccess
        // The requested 3B Q4 model is ~2 GB. Reject unusually large models before mapping.
        private const val MAX_MODEL_BYTES = 3L * 1024 * 1024 * 1024
    }

    private external fun nativePrepareLoad()
    private external fun nativePrepareGeneration(handle: Long)
    private external fun nativeLoad(path: String): Long
    private external fun nativeGenerate(
        handle: Long, roles: Array<String>, content: Array<ByteArray>, maxTokens: Int, sink: TokenSink
    ): ByteArray
    private external fun nativeCancel(handle: Long)
    private external fun nativeFree(handle: Long)

    suspend fun load(file: File) = withContext(Dispatchers.IO) {
        operation.withLock {
            if (synchronized(handleLock) { handle != 0L }) return@withLock
            check(nativeAvailable) { "Native engine unavailable. Install the arm64 build of IRA." }
            check(file.isFile && file.length() in 4..MAX_MODEL_BYTES) { "The model is missing or larger than IRA's 3 GiB model limit." }
            val context = currentCoroutineContext()
            synchronized(handleLock) {
                context.ensureActive()
                nativePrepareLoad()
            }
            val loaded = nativeLoad(file.absolutePath)
            check(loaded != 0L) { "The model could not be loaded." }
            synchronized(handleLock) { handle = loaded }
        }
    }

    suspend fun generate(messages: List<Message>, onProgress: (String) -> Unit): String = withContext(Dispatchers.Default) {
        operation.withLock {
            val context = currentCoroutineContext()
            val pointer = synchronized(handleLock) {
                context.ensureActive()
                check(handle != 0L) { "Download and load the model first." }
                nativePrepareGeneration(handle)
                handle
            }
            val sink = TokenSink { bytes ->
                // A token can end midway through a Hindi/emoji UTF-8 character. The
                // next snapshot contains it in full, so omit incomplete bytes for now.
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                onProgress(decoder.decode(ByteBuffer.wrap(bytes)).toString())
            }
            val safe = messages.map { it.copy(content = it.content.replace("<|", "‹|")) }
            val result = nativeGenerate(pointer, safe.map { it.role }.toTypedArray(),
                safe.map { it.content.toByteArray(Charsets.UTF_8) }.toTypedArray(), 150, sink)
            result.toString(Charsets.UTF_8).trim()
        }
    }

    fun isLoaded(): Boolean = synchronized(handleLock) { handle != 0L }

    fun cancel() = synchronized(handleLock) { if (nativeAvailable) nativeCancel(handle) }

    suspend fun unload() = withContext(Dispatchers.IO) {
        operation.withLock {
            synchronized(handleLock) {
                if (handle != 0L) nativeFree(handle)
                handle = 0L
            }
        }
    }
}
