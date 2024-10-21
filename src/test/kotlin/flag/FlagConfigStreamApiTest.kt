package com.amplitude.experiment.flag

import com.amplitude.experiment.Experiment
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.SseStream
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.verify
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

class FlagConfigStreamApiTest {
    private val onUpdateCapture = slot<((String) -> Unit)>()
    private val onErrorCapture = slot<((Throwable?) -> Unit)>()

    @BeforeTest
    fun beforeTest() {
        mockkConstructor(SseStream::class)

        every { anyConstructed<SseStream>().connect(capture(onUpdateCapture), capture(onErrorCapture)) } answers {
            Thread.sleep(1000)
        }
        every { anyConstructed<SseStream>().cancel() } answers {
            Thread.sleep(1000)
        }
    }

    @AfterTest
    fun afterTest() {
        clearAllMocks()
    }

    private fun setupApi(
        deploymentKey: String = "",
        serverUrl: HttpUrl = "http://localhost".toHttpUrl(),
        connTimeout: Long = 2000
    ): FlagConfigStreamApi {
        val api = FlagConfigStreamApi(deploymentKey, serverUrl, OkHttpClient(), connTimeout, 10000)

        return api
    }

    @Test
    fun `Test passes correct arguments`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl())
        var data: Array<List<EvaluationFlag>> = arrayOf()
        var err: Array<Throwable?> = arrayOf()

        val run = async {
            api.connect({ d ->
                data += d
            }, { d ->
                data += d
            }, { t ->
                err += t
            })
        }
        Thread.sleep(100)
        onUpdateCapture.captured("[{\"key\":\"flagkey\",\"variants\":{},\"segments\":[]}]")
        run.join()

        verify { anyConstructed<SseStream>().connect(any(), any()) }
        assertContentEquals(arrayOf(listOf(EvaluationFlag("flagkey", emptyMap(), emptyList()))), data)

        api.close()
    }

    @Test
    fun `Test conn timeout doesn't block`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl(), 500)
        try {
            api.connect()
            fail("Timeout not thrown")
        } catch (_: FlagConfigStreamApiConnTimeoutError) {
        }
        Thread.sleep(100)
        verify { anyConstructed<SseStream>().cancel() }
    }

    @Test
    fun `Test init update failure throws`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl(), 2000)

        try {
            api.connect({
                Thread.sleep(2100) // Update time is not included in connection timeout.
                throw Error()
            })
            fail("Timeout not thrown")
        } catch (_: FlagConfigStreamApiConnTimeoutError) {
        }
        verify { anyConstructed<SseStream>().cancel() }
    }

    @Test
    fun `Test init update fallbacks to onUpdate when onInitUpdate = null`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl(), 2000)

        try {
            api.connect(null, {
                Thread.sleep(2100) // Update time is not included in connection timeout.
                throw Error()
            })
            fail("Timeout not thrown")
        } catch (_: FlagConfigStreamApiConnTimeoutError) {
        }
        verify { anyConstructed<SseStream>().cancel() }
    }

    @Test
    fun `Test error is passed through onError`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl(), 2000)
        var err: Array<Throwable?> = arrayOf()

        val run = async {
            api.connect({ d ->
                assertEquals(listOf(), d)
            }, { d ->
                assertEquals(listOf(), d)
            }, { t ->
                err += t
            })
        }
        Thread.sleep(100)
        onUpdateCapture.captured("[]")
        run.join()

        assertEquals(0, err.size)
        onErrorCapture.captured(Error("Haha error"))
        assertEquals("Stream error", err[0]?.message)
        assertEquals("Haha error", err[0]?.cause?.message)
        assertEquals(1, err.size)
        verify { anyConstructed<SseStream>().cancel() }
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
