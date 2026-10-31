package com.ira.assistant.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Strict URL and filename rules. Never attach the Hugging Face token to pack requests. */
object RemoteSourcePolicy {
    private val modelName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*\\.gguf$", RegexOption.IGNORE_CASE)
    private val repoId = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*$")
    private val token = Regex("^[A-Za-z0-9_-]+$")

    fun validateModelName(filename: String): String {
        require(modelName.matches(filename) && filename != ".gguf" && ".." !in filename) { "Invalid model filename in the bundled configuration." }
        return filename
    }

    fun modelUrl(repo: String, filename: String): HttpUrl {
        require(repoId.matches(repo) && ".." !in repo) { "Invalid Hugging Face repository ID." }
        validateModelName(filename)
        return HttpUrl.Builder().scheme("https").host("huggingface.co")
            .addPathSegments(repo).addPathSegment("resolve").addPathSegment("main").addPathSegment(filename).build()
    }

    fun validateToken(value: String): String {
        val clean = value.trim()
        require(clean.length <= 300 && (clean.isEmpty() || token.matches(clean))) { "Invalid Hugging Face token." }
        return clean
    }

    fun packBaseUrl(value: String): HttpUrl {
        val parsed = value.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Enter a valid HTTPS language-pack URL.")
        require(parsed.scheme == "https" && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.query == null && parsed.fragment == null) {
            "Use an HTTPS base URL without credentials, a query or fragment."
        }
        return parsed
    }
}
