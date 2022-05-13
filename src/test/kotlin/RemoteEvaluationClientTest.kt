package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import kotlin.test.fail

private const val API_KEY = "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"

class RemoteEvaluationClientTest {

    init {
        Logger.implementation = SystemLogger(true)
    }

    private val testFlagKey = "sdk-ci-test"
    private val testVariant = Variant("on", "payload")

    private val testUser = ExperimentUser(userId = "test_user")

    @Test
    fun `test fetch`() {
        val client = RemoteEvaluationClient(
            API_KEY,
            RemoteEvaluationConfig(),
        )
        val variants = client.fetchAsync(testUser).get()
        Assert.assertNotNull(variants)
        val variant = variants[testFlagKey]
        Assert.assertEquals(testVariant, variant)
    }

    @Test
    fun `test fetch, timeout no retries, failure`() {
        val client = RemoteEvaluationClient(
            API_KEY,
            RemoteEvaluationConfig.builder()
                .fetchTimeoutMillis(1)
                .fetchRetries(0)
                .build(),
        )
        try {
            client.fetchAsync(testUser).get()
        } catch (t: Throwable) {
            // Success
            return
        }
        fail("fetch should fail due to timeout")
    }

    @Test
    fun `test fetch, cancel during retries`() {
        val client = RemoteEvaluationClient(
            API_KEY,
            RemoteEvaluationConfig.builder()
                .fetchTimeoutMillis(1)
                .fetchRetries(10)
                .fetchRetryBackoffScalar(1.0)
                .fetchRetryBackoffMinMillis(1000)
                .build(),
        )
        val start = Date()
        try {
            val future = client.fetchAsync(testUser)
            asyncFuture(2500) {
                future.cancel(true)
            }
            future.get()
            fail("future should be failed with cancellation")
        } catch (t: Throwable) {
            val fail = Date()
            assert(t is CancellationException)
            val duration = fail.time - start.time
            Assert.assertTrue(duration in 2500..2599)
        }
    }
}

@Suppress("SameParameterValue")
private fun <T> asyncFuture(
    delayMillis: Long = 0L,
    block: () -> T
): CompletableFuture<T> = runBlocking {
    async {
        delay(delayMillis)
        block()
    }.asCompletableFuture()
}
