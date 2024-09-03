package com.amplitude.experiment.util

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.RemoteEvaluationClient
import io.mockk.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.mockito.Mockito
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class SseStreamTest {
    private val listenerCapture = slot<EventSourceListener>()
    val clientMock = mockk<OkHttpClient>()
    private val es = mockk<EventSource>("mocked es")

    private var data: List<String> = listOf()
    private var err: List<Throwable?> = listOf()

    @BeforeTest
    fun beforeTest() {
        mockkStatic("com.amplitude.experiment.util.RequestKt")

        justRun { es.cancel() }
        every { clientMock.newEventSource(any(), capture(listenerCapture)) } returns es

        mockkConstructor(OkHttpClient.Builder::class)
        every { anyConstructed<OkHttpClient.Builder>().build() } returns clientMock

    }

    private fun setupStream(
        reconnTimeout: Long = 5000
    ): SseStream {
        val stream = SseStream("authtoken", "http://localhost".toHttpUrl(), OkHttpClient(), 1000, 1000, reconnTimeout, 0)

        stream.onUpdate = { d ->
            data += d
        }
        stream.onError = { t ->
            err += t
        }
        return stream
    }

    @Test
    fun `Test SseStream connect`() {
        val stream = setupStream()
        stream.connect()

        listenerCapture.captured.onEvent(es, null, null, "somedata")
        assertEquals(listOf("somedata"), data)
        listenerCapture.captured.onFailure(es, null, null)
        assertEquals("Unknown stream failure", err[0]?.message)
        listenerCapture.captured.onEvent(es, null, null, "nodata")
        assertEquals(listOf("somedata"), data)

        stream.cancel()
    }

    @Test
    fun `Test SseStream keep alive data omits`() {
        val stream = setupStream(1000)
        stream.connect()

        listenerCapture.captured.onEvent(es, null, null, "somedata")
        assertEquals(listOf("somedata"), data)
        listenerCapture.captured.onEvent(es, null, null, " ")
        assertEquals(listOf("somedata"), data)

        stream.cancel()
    }

    @Test
    fun `Test SseStream reconnects`() {
        val stream = setupStream(1000)
        stream.connect()

        listenerCapture.captured.onEvent(es, null, null, "somedata")
        assertEquals(listOf("somedata"), data)
        verify(exactly = 1) {
            clientMock.newEventSource(allAny(), allAny())
        }

        Thread.sleep(1100) // Wait 1s for reconnection

        listenerCapture.captured.onEvent(es, null, null, "somedata")
        assertEquals(listOf("somedata", "somedata"), data)
        verify(exactly = 2) {
            clientMock.newEventSource(allAny(), allAny())
        }
    }
}
