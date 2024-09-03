package com.amplitude.experiment.flag

import com.amplitude.experiment.Experiment
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.SseStream
import io.mockk.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class FlagConfigStreamApiTest {
    private val onUpdateCapture = slot<((String) -> Unit)>()
    private val onErrorCapture = slot<((Throwable?) -> Unit)>()

    private var data: Array<List<EvaluationFlag>> = arrayOf()
    private var err: Array<Throwable?> = arrayOf()

    @BeforeTest
    fun beforeTest() {
        mockkConstructor(SseStream::class)

        every { anyConstructed<SseStream>().connect() } answers {
            Thread.sleep(1000)
        }
        every { anyConstructed<SseStream>().cancel() } answers {
            Thread.sleep(1000)
        }
        every { anyConstructed<SseStream>().onUpdate = capture(onUpdateCapture) } answers {}
        every { anyConstructed<SseStream>().onError = capture(onErrorCapture) } answers {}
    }

    private fun setupApi(
        deploymentKey: String = "",
        serverUrl: HttpUrl = "http://localhost".toHttpUrl(),
        connTimeout: Long = 2000
    ): FlagConfigStreamApi {
        val api = FlagConfigStreamApi(deploymentKey, serverUrl, OkHttpClient(), connTimeout, 10000)

        api.onUpdate = { d ->
            data += d
        }
        api.onError = { t ->
            err += t
        }
        return api
    }

    @Test
    fun `Test passes correct arguments`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl())
        api.onInitUpdate = { d ->
            data += d
        }

        val run = async {
            api.connect()
        }
        Thread.sleep(100)
        onUpdateCapture.captured("[{\"key\":\"flagkey\",\"variants\":{},\"segments\":[]}]")
        run.join()

        verify { anyConstructed<SseStream>().connect() }
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

        api.onInitUpdate = { d ->
            Thread.sleep(2100) // Update time is not included in connection timeout.
            throw Error()
        }
        api.onUpdate = null
        try {
            api.connect()
            fail("Timeout not thrown")
        } catch (_: FlagConfigStreamApiConnTimeoutError) {
        }
        verify { anyConstructed<SseStream>().cancel() }
    }

    @Test
    fun `Test init update fallbacks to onUpdate when onInitUpdate = null`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl(), 2000)

        api.onInitUpdate = null
        api.onUpdate = { d ->
            Thread.sleep(2100) // Update time is not included in connection timeout.
            throw Error()
        }
        try {
            api.connect()
            fail("Timeout not thrown")
        } catch (_: FlagConfigStreamApiConnTimeoutError) {
        }
        verify { anyConstructed<SseStream>().cancel() }
    }

    @Test
    fun `Test error is passed through onError`() {
        val api = setupApi("deplkey", "https://test.example.com".toHttpUrl(), 2000)

        val run = async {
            api.connect()
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
