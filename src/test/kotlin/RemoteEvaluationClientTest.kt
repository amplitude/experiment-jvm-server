package com.amplitude.experiment

import com.amplitude.experiment.util.FetchException
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import okhttp3.OkHttpClient
import org.junit.Assert
import java.util.Date
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.fail

private const val API_KEY = "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"

/**
 * To assert two variants. These fields are not consistent across evaluation, simply assert not null.
 * - metadata.evaluationId
 */
fun assertVariantEquals(
    expected: Variant,
    actual: Variant?,
) {
    Assert.assertEquals(expected.key, actual?.key)
    Assert.assertEquals(expected.value, actual?.value)
    Assert.assertEquals(expected.payload, actual?.payload)
    if (expected.metadata?.get("evaluationId") != null) {
        Assert.assertNotNull(actual?.metadata?.get("evaluationId"))
    }
}

class RemoteEvaluationClientTest {

    init {
        Logger.implementation = SystemLogger(false)
    }

    private val testFlagKey = "sdk-ci-test"
    private val testVariant = Variant(key = "on", value = "on", payload = "payload", metadata = mapOf("evaluationId" to ""))

    private val testUser = ExperimentUser(userId = "test_user")

    @BeforeTest
    fun setUp() = MockKAnnotations.init(this, relaxUnitFun = true)

    @Test
    fun `test fetch`() {
        val client = RemoteEvaluationClient(
            API_KEY,
            RemoteEvaluationConfig(debug = true),
        )
        val start = System.nanoTime()
        val variants = client.fetch(testUser).get()
        val end = System.nanoTime()
        val dur = (end - start) / 1000.0 / 1000.0
        println(dur)
        Assert.assertNotNull(variants)
        val variant = variants[testFlagKey]
        assertVariantEquals(testVariant, variant)
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
            client.fetch(testUser).get()
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
            val future = client.fetch(testUser)
            async(2500) {
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

    // @Test
    // fun test() {
    //     val client = RemoteEvaluationClient(API_KEY, RemoteEvaluationConfig())
    //     val sem = Semaphore(2)
    //     while(true) {
    //         sem.acquire()
    //         val start = System.nanoTime()
    //         client.fetch(testUser).handle { _, throwable ->
    //             throwable?.printStackTrace()
    //             sem.release()
    //             val end = System.nanoTime()
    //             val dur = (end - start) / 1000.0 / 1000.0
    //             println(String.format("%.2fms", dur))
    //         }
    //     }
    // }

    @Test
    fun `fetch retry with different response codes`() {
        // Response code, error message, and whether retry should be called
        val testData = listOf(
            Triple(300, "Fetch Exception 300", 2),
            Triple(400, "Fetch Exception 400", 1),
            Triple(429, "Fetch Exception 429", 2),
            Triple(500, "Fetch Exception 500", 2),
            Triple(0, "Other Exception", 2)
        )

        testData.forEach { (responseCode, errorMessage, fetchCalls) ->
            val config = RemoteEvaluationConfig(fetchRetries = 1, debug = true)
            val client = spyk(RemoteEvaluationClient("apiKey", config), recordPrivateCalls = true)
            // Mock the private method to throw FetchException or other exceptions
            every { client["doFetch"](any<ExperimentUser>(), any<Long>(), isNull<FetchOptions>()) } answers {
                val future = CompletableFuture<Map<String, Variant>>()
                if (responseCode == 0) {
                    future.completeExceptionally(Exception(errorMessage))
                } else {
                    future.completeExceptionally(FetchException(responseCode, errorMessage))
                }
                future
            }

            try {
                client.fetch(ExperimentUser("test_user")).get()
            } catch (t: Throwable) {
                // catch exception
            }

            verify(exactly = fetchCalls) { client["doFetch"](any<ExperimentUser>(), any<Long>(), isNull<FetchOptions>()) }
        }
    }

    @Test
    fun `test fetch with fetch options`() {
        val client = RemoteEvaluationClient(
            API_KEY,
            RemoteEvaluationConfig(debug = true),
        )

        // Use reflection to spy on private httpClient field
        val httpClient = spyk(OkHttpClient())
        val httpClientField = RemoteEvaluationClient::class.java.getDeclaredField("httpClient")
        httpClientField.isAccessible = true
        httpClientField.set(client, httpClient)

        val variants = client.fetch(
            testUser,
            FetchOptions.builder()
                .setTracksAssignment(true)
                .setTracksExposure(false)
                .build()
        ).get()

        Assert.assertNotNull(variants)
        assertVariantEquals(testVariant, variants[testFlagKey])

        verify {
            httpClient.newCall(
                match {
                    it.headers["X-Amp-Exp-Track"] == "track" && it.headers["X-Amp-Exp-Exposure-Track"] == "no-track"
                }
            )
        }

        client.fetch(testUser, FetchOptions(tracksAssignment = false, tracksExposure = true)).get()
        verify {
            httpClient.newCall(
                match {
                    it.headers["X-Amp-Exp-Track"] == "no-track" && it.headers["X-Amp-Exp-Exposure-Track"] == "track"
                }
            )
        }

        client.fetch(testUser, FetchOptions()).get()
        verify {
            httpClient.newCall(
                match {
                    it.headers["X-Amp-Exp-Track"] == null && it.headers["X-Amp-Exp-Exposure-Track"] == null
                }
            )
        }
    }
}

@Suppress("SameParameterValue")
private fun <T> async(delayMillis: Long = 0L, block: () -> T): CompletableFuture<T> {
    return if (delayMillis == 0L) {
        CompletableFuture.supplyAsync(block)
    } else {
        val future = CompletableFuture<T>()
        Experiment.scheduler.schedule({
            try {
                future.complete(block.invoke())
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }, delayMillis, TimeUnit.MILLISECONDS)
        future
    }
}
