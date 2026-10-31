package com.ira.assistant.core

import android.content.Context
import android.os.StatFs
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

private const val MAX_MODEL_SIZE = 3L * 1024 * 1024 * 1024
private const val MIN_MODEL_SIZE = 100L * 1024 * 1024

data class TransferProgress(val bytes: Long = 0, val total: Long? = null) {
    val percent: Int? get() = total?.takeIf { it > 0 }?.let { ((bytes * 100) / it).coerceIn(0, 100).toInt() }
}

data class PackSyncResult(val updated: List<String>, val failed: List<String>)

class AssetSyncManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        // Never follow an HTTPS redirect to cleartext HTTP. OkHttp also strips the
        // Authorization header on cross-host redirects to Hugging Face's CDN.
        .followSslRedirects(false)
        .build()
    private val configuration: JSONObject by lazy {
        context.assets.open("config/settings.json").bufferedReader().use { JSONObject(it.readText()) }
    }
    val repoId: String get() = configuration.getJSONObject("model").getString("repo_id")
    val modelFileName: String get() = configuration.getJSONObject("model").getString("filename")

    fun modelFile(): File {
        val external = context.getExternalFilesDir(null) ?: throw IOException("App-specific model storage is unavailable.")
        val filename = RemoteSourcePolicy.validateModelName(modelFileName)
        return File(external, "assets/models/llm/$filename")
    }

    fun hasModel(): Boolean = runCatching {
        val file = modelFile()
        file.isFile && file.length() in MIN_MODEL_SIZE..MAX_MODEL_SIZE && hasGgufHeader(file)
    }.getOrDefault(false)

    private fun hasGgufHeader(file: File): Boolean = file.inputStream().use { input ->
        val magic = ByteArray(4)
        input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
    }

    /** The token is used only for this HTTPS Hugging Face request and is never persisted. */
    suspend fun downloadModel(token: String, progress: (TransferProgress) -> Unit): File = withContext(Dispatchers.IO) {
        val url = RemoteSourcePolicy.modelUrl(repoId, modelFileName)
        val cleanToken = RemoteSourcePolicy.validateToken(token)
        val target = modelFile()
        if (hasModel()) return@withContext target
        val parent = target.parentFile ?: throw IOException("Model directory is unavailable.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create the model directory.")
        val temp = File(parent, "${target.name}.part")
        val builder = Request.Builder().url(url).header("User-Agent", "IRA-Android/1.0")
        if (cleanToken.isNotBlank()) builder.header("Authorization", "Bearer $cleanToken")
        val call = client.newCall(builder.build())
        try {
            call.executeCancellable { response ->
                if (!response.isSuccessful) throw IOException("Model download failed (HTTP ${response.code}). Check the repository, token and model access.")
                val body = response.body ?: throw IOException("The model response was empty.")
                val length = body.contentLength().takeIf { it >= 0 }
                if (length != null && length !in MIN_MODEL_SIZE..MAX_MODEL_SIZE) throw IOException("The model size is outside IRA's allowed range.")
                if (length != null && StatFs(parent.absolutePath).availableBytes < length + 128L * 1024 * 1024) {
                    throw IOException("Not enough storage for the model download.")
                }
                var downloaded = 0L
                var lastReported = -1
                progress(TransferProgress(0, length))
                body.byteStream().use { input ->
                    temp.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            downloaded += read
                            if (downloaded > MAX_MODEL_SIZE) throw IOException("The model exceeds IRA's 3 GiB download limit.")
                            output.write(buffer, 0, read)
                            val reportMarker = length?.let { ((downloaded * 100) / it).toInt() }
                                ?: (downloaded / (1024 * 1024)).toInt()
                            if (reportMarker != lastReported) {
                                lastReported = reportMarker
                                progress(TransferProgress(downloaded, length))
                            }
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (length != null && downloaded != length) throw IOException("The model download was incomplete.")
                if (downloaded < MIN_MODEL_SIZE || !hasGgufHeader(temp)) throw IOException("The downloaded file is not a valid GGUF model.")
                // Both files live in the same app-specific directory. Keep the old file
                // until the new download has fully completed and passed validation.
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                progress(TransferProgress(downloaded, length))
                target
            }
        } finally {
            call.cancel()
            if (temp.exists()) temp.delete()
        }
    }

    suspend fun syncLanguagePacks(baseUrl: String): PackSyncResult = withContext(Dispatchers.IO) {
        val base = RemoteSourcePolicy.packBaseUrl(baseUrl)
        val dir = File(context.filesDir, "assets/language_packs")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Unable to create the language-pack directory.")
        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (language in LANGUAGE_CODES) {
            currentCoroutineContext().ensureActive()
            val name = "$language.json"
            val request = Request.Builder().url(base.newBuilder().addPathSegment(name).build())
                .header("User-Agent", "IRA-Android/1.0").build()
            val call = client.newCall(request)
            try {
                call.executeCancellable { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty response")
                    if (body.contentLength() > MAX_LANGUAGE_PACK_BYTES) throw IOException("Language pack too large")
                    val bytes = body.byteStream().use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > MAX_LANGUAGE_PACK_BYTES) throw IOException("Language pack too large")
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val json = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                    LanguagePacks.parse(json)
                    val atomic = AtomicFile(File(dir, name))
                    val output = atomic.startWrite()
                    try {
                        output.write(bytes)
                        atomic.finishWrite(output)
                    } catch (failure: Exception) {
                        atomic.failWrite(output)
                        throw failure
                    }
                    updated += name
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Do not expose URL contents, server responses or notification data in logs.
                failed += name
            } finally {
                call.cancel()
            }
        }
        PackSyncResult(updated, failed)
    }
}
