package com.ira.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteSourcePolicyTest {
    @Test fun acceptsOnlySafeTrustedPackBaseUrls() {
        val base = RemoteSourcePolicy.packBaseUrl("https://example.com/api/packs/")
        assertEquals("https://example.com/api/packs/hi_IN.json", base.newBuilder().addPathSegment("hi_IN.json").build().toString())
        listOf("http://example.com/packs", "https://user:secret@example.com", "https://example.com?token=secret", "https://example.com/#x")
            .forEach { invalid -> assertThrows(IllegalArgumentException::class.java) { RemoteSourcePolicy.packBaseUrl(invalid) } }
    }

    @Test fun constructsOnlyHuggingFaceModelPaths() {
        assertEquals("https://huggingface.co/bartowski/repo/resolve/main/model.gguf",
            RemoteSourcePolicy.modelUrl("bartowski/repo", "model.gguf").toString())
        assertThrows(IllegalArgumentException::class.java) { RemoteSourcePolicy.modelUrl("../secret", "model.gguf") }
        assertThrows(IllegalArgumentException::class.java) { RemoteSourcePolicy.modelUrl("a/b", "../model.gguf") }
        assertThrows(IllegalArgumentException::class.java) { RemoteSourcePolicy.validateToken("hf_x\nInjected: value") }
        assertEquals("hf_example", RemoteSourcePolicy.validateToken(" hf_example "))
    }
}
