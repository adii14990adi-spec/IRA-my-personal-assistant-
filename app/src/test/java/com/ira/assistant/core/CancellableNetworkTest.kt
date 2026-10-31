package com.ira.assistant.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CancellableNetworkTest {
    private class BlockingCall : Call {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        override fun request(): Request = Request.Builder().url("https://example.com/").build()
        override fun execute(): Response {
            started.countDown()
            if (!cancelled.await(5, TimeUnit.SECONDS)) throw IOException("Cancellation timed out")
            throw IOException("Socket cancelled")
        }
        override fun enqueue(responseCallback: Callback) = error("Unused")
        override fun cancel() { cancelled.countDown() }
        override fun isExecuted() = started.count == 0L
        override fun isCanceled() = cancelled.count == 0L
        override fun timeout(): Timeout = Timeout.NONE
        public override fun clone(): Call = BlockingCall()
    }

    @Test fun cancellingDownloadClosesBlockedCall() = runBlocking {
        val call = BlockingCall()
        val job = launch(Dispatchers.IO) { call.executeCancellable { } }
        assertTrue(withContext(Dispatchers.IO) { call.started.await(3, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
    }
}
