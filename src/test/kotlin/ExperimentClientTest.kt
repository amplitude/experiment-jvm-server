package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Test
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.fail

private const val API_KEY = "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"

class ExperimentClientTest {

    init {
        Logger.implementation = SystemLogger(true)
    }

    private val serverVariants = mapOf("sdk-ci-test" to Variant("on", "payload"))

    private val client = ExperimentClient(
        API_KEY,
        ExperimentConfig(),
        OkHttpClient(),
    )

    private val noRetriesTimeoutFailureClient = ExperimentClient(
        API_KEY,
        ExperimentConfig.builder()
            .fetchTimeoutMillis(1)
            .fetchRetries(0)
            .build(),
        OkHttpClient(),
    )

    private val timeoutRetriesSuccessClient = ExperimentClient(
        API_KEY,
        ExperimentConfig.builder()
            .fetchTimeoutMillis(1)
            .build(),
        OkHttpClient(),
    )

    private val timeoutRetriesFailureClient = ExperimentClient(
        API_KEY,
        ExperimentConfig.builder()
            .fetchTimeoutMillis(1)
            .fetchRetryTimeoutMillis(1)
            .fetchRetries(2)
            .fetchRetryBackoffScalar(10.0)
            .fetchRetryBackoffMinMillis(1000)
            .fetchRetryBackoffMaxMillis(2000)
            .build(),
        OkHttpClient(),
    )

    private val testCancellationClient = ExperimentClient(
        API_KEY,
        ExperimentConfig.builder()
            .fetchTimeoutMillis(1)
            .fetchRetryTimeoutMillis(1)
            .fetchRetries(10)
            .fetchRetryBackoffScalar(1.0)
            .fetchRetryBackoffMinMillis(1000)
            .build(),
        OkHttpClient(),
    )

    private val testUser = ExperimentUser(userId = "test_user")

    @Test
    fun `test fetch`() {
        val variants = client.fetch(testUser).get()
        Assert.assertNotNull(variants)
        Assert.assertEquals(serverVariants, variants)
    }

    @Test
    fun `test fetch, timeout no retries, failure`() {
        try {
            noRetriesTimeoutFailureClient.fetch(testUser).get()
        } catch(t: Throwable) {
            // Success
            return
        }
        fail("fetch should fail due to timeout")
    }


    @Test
    fun `test fetch, timeout with retries, success`() {
        val variants = timeoutRetriesSuccessClient.fetch(testUser).get()
        Assert.assertNotNull(variants)
        Assert.assertEquals(serverVariants, variants)
    }

    @Test
    fun `test fetch, timeout with retries, failure`() {
        val start = Date()
        try {
            timeoutRetriesFailureClient.fetch(testUser).get()
        } catch(t: Throwable) {
            // Success
            val fail = Date()
            val duration = fail.time - start.time
            Assert.assertTrue(duration in 3000..3099)
        }
    }

    @Test
    fun `test fetch, cancel during retries`() {
        val start = Date()
        try {
            val future = testCancellationClient.fetch(testUser)
            async(2500) {
                future.cancel(true)
            }
            future.get()
            fail("future should be failed with cancellation")
        } catch(t: Throwable) {
            val fail = Date()
            assert(t is CancellationException)
            val duration = fail.time - start.time
            Assert.assertTrue(duration in 2500..2599)
        }
    }
}

@Suppress("SameParameterValue")
private fun <T> async(delayMillis: Long = 0L, block: () -> T): CompletableFuture<T> {
    return if (delayMillis == 0L) {
        CompletableFuture.supplyAsync(block)
    } else {
        val future = CompletableFuture<T>()
        CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS).execute {
            try {
                future.complete(block.invoke())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }
        future
    }
}