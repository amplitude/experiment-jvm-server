package com.amplitude.experiment.flag

import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.SseStream
import io.mockk.*
import kotlin.test.*

private val FLAG1 = EvaluationFlag("key1", emptyMap(), emptyList())
private val FLAG2 = EvaluationFlag("key2", emptyMap(), emptyList())
class FlagConfigPollerTest {
    private var fetchApi = mockk<FlagConfigApi>()
    private var storage = InMemoryFlagConfigStorage()

    @BeforeTest
    fun beforeEach() {
        fetchApi = mockk<FlagConfigApi>()
        storage = InMemoryFlagConfigStorage()
    }

    @Test
    fun `Test Poller`() {
        every { fetchApi.getFlagConfigs() } returns emptyList()
        val poller = FlagConfigPoller(fetchApi, storage, null, null, LocalEvaluationConfig(flagConfigPollerIntervalMillis = 1000))
        var errorCount = 0
        poller.start { errorCount++ }

        // start() polls
        verify(exactly = 1) { fetchApi.getFlagConfigs() }
        assertEquals(0, storage.getFlagConfigs().size)

        // Poller polls every 1s interval
        every { fetchApi.getFlagConfigs() } returns listOf(FLAG1)
        Thread.sleep(1100)
        verify(exactly = 2) { fetchApi.getFlagConfigs() }
        assertEquals(1, storage.getFlagConfigs().size)
        assertEquals(mapOf(FLAG1.key to FLAG1), storage.getFlagConfigs())
        Thread.sleep(1100)
        verify(exactly = 3) { fetchApi.getFlagConfigs() }

        // Stop poller stops
        poller.stop()
        Thread.sleep(1100)
        verify(exactly = 3) { fetchApi.getFlagConfigs() }

        // Restart poller
        every { fetchApi.getFlagConfigs() } returns listOf(FLAG1, FLAG2)
        poller.start()
        verify(exactly = 4) { fetchApi.getFlagConfigs() }
        Thread.sleep(1100)
        verify(exactly = 5) { fetchApi.getFlagConfigs() }
        assertEquals(2, storage.getFlagConfigs().size)
        assertEquals(mapOf(FLAG1.key to FLAG1, FLAG2.key to FLAG2), storage.getFlagConfigs())

        // No errors
        assertEquals(0, errorCount)

        poller.shutdown()
    }

    @Test
    fun `Test Poller start fails`(){
        every { fetchApi.getFlagConfigs() } answers { throw Error("Haha error") }
        val poller = FlagConfigPoller(fetchApi, storage, null, null, LocalEvaluationConfig(flagConfigPollerIntervalMillis = 1000))
        var errorCount = 0
        try {
            poller.start { errorCount++ }
            fail("Poller start error not throwing")
        } catch (_: Throwable) {
        }
        verify(exactly = 1) { fetchApi.getFlagConfigs() }

        // Poller stops
        Thread.sleep(1100)
        verify(exactly = 1) { fetchApi.getFlagConfigs() }
        assertEquals(0, errorCount)

        poller.shutdown()
    }

    @Test
    fun `Test Poller poll fails`(){
        every { fetchApi.getFlagConfigs() } returns emptyList()
        val poller = FlagConfigPoller(fetchApi, storage, null, null, LocalEvaluationConfig(flagConfigPollerIntervalMillis = 1000))
        var errorCount = 0
        poller.start { errorCount++ }

        // Poller start success
        verify(exactly = 1) { fetchApi.getFlagConfigs() }
        assertEquals(0, errorCount)

        // Next poll subsequent fails
        every { fetchApi.getFlagConfigs() } answers { throw Error("Haha error") }
        Thread.sleep(1100)
        verify(exactly = 2) { fetchApi.getFlagConfigs() }
        assertEquals(1, errorCount)

        // Poller stops
        Thread.sleep(1100)
        verify(exactly = 2) { fetchApi.getFlagConfigs() }
        assertEquals(1, errorCount)

        poller.shutdown()
    }
}

class FlagConfigStreamerTest {
    private val onUpdateCapture = slot<((List<EvaluationFlag>) -> Unit)>()
    private val onErrorCapture = slot<((Throwable?) -> Unit)>()
    private var streamApi = mockk<FlagConfigStreamApi>()
    private var storage = InMemoryFlagConfigStorage()
    private val config = LocalEvaluationConfig(streamUpdates = true, streamServerUrl = "", streamFlagConnTimeoutMillis = 2000)

    @BeforeTest
    fun beforeEach() {
        streamApi = mockk<FlagConfigStreamApi>()
        storage = InMemoryFlagConfigStorage()

        justRun { streamApi.onUpdate = capture(onUpdateCapture) }
        justRun { streamApi.onError = capture(onErrorCapture) }
    }

    @Test
    fun `Test Poller`() {
        justRun { streamApi.connect() }
        val streamer = FlagConfigStreamer(streamApi, storage, null, null, config)
        var errorCount = 0
        streamer.start { errorCount++ }

        // Streamer starts
        verify(exactly = 1) { streamApi.connect() }

        // Verify update callback updates storage
        onUpdateCapture.captured(emptyList())
        assertEquals(0, storage.getFlagConfigs().size)
        onUpdateCapture.captured(listOf(FLAG1))
        assertEquals(mapOf(FLAG1.key to FLAG1), storage.getFlagConfigs())
        onUpdateCapture.captured(listOf(FLAG1, FLAG2))
        assertEquals(mapOf(FLAG1.key to FLAG1, FLAG2.key to FLAG2), storage.getFlagConfigs())

        // No extra connect calls
        verify(exactly = 1) { streamApi.connect() }

        // No errors
        assertEquals(0, errorCount)
    }

    @Test
    fun `Test Streamer start fails`(){
        every { streamApi.connect() } answers { throw Error("Haha error") }
        val streamer = FlagConfigStreamer(streamApi, storage, null, null, config)
        var errorCount = 0
        try {
            streamer.start { errorCount++ }
            fail("Streamer start error not throwing")
        } catch (_: Throwable) {
        }
        verify(exactly = 1) { streamApi.connect() }
        assertEquals(0, errorCount) // No error callback as it throws directly
    }

    @Test
    fun `Test Streamer stream fails`(){
        every { streamApi.connect() } answers { throw Error("Haha error") }
        val streamer = FlagConfigStreamer(streamApi, storage, null, null, config)
        var errorCount = 0
        streamer.start { errorCount++ }

        // Stream start success
        verify(exactly = 1) { streamApi.connect() }
        onUpdateCapture.captured(listOf(FLAG1))
        assertEquals(mapOf(FLAG1.key to FLAG1), storage.getFlagConfigs())
        assertEquals(0, errorCount)

        // Stream fails
        onErrorCapture.captured(Error("Haha error"))
        assertEquals(1, errorCount) // Error callback is called
    }
}


class FlagConfigFallbackRetryWrapperTest {
    private val mainOnErrorCapture = slot<(() -> Unit)>()
    private val fallbackOnErrorCapture = slot<(() -> Unit)>()

    private var mainUpdater = mockk<FlagConfigUpdater>()
    private var fallbackUpdater = mockk<FlagConfigUpdater>()
    @BeforeTest
    fun beforeEach() {
        mainUpdater = mockk<FlagConfigUpdater>()
        fallbackUpdater = mockk<FlagConfigUpdater>()

        justRun { mainUpdater.start(capture(mainOnErrorCapture)) }
        justRun { mainUpdater.stop() }
        justRun { mainUpdater.shutdown() }
        justRun { fallbackUpdater.start(capture(fallbackOnErrorCapture)) }
        justRun { fallbackUpdater.stop() }
        justRun { fallbackUpdater.shutdown() }
    }

    @Test
    fun `Test FallbackRetryWrapper main updater all success`() {
        val wrapper = FlagConfigFallbackRetryWrapper(mainUpdater, fallbackUpdater, 1000, 0)
        var errorCount = 0

        // Main starts
        wrapper.start { errorCount++ }
        verify(exactly = 1) { mainUpdater.start(any()) }
        verify(exactly = 0) { fallbackUpdater.start() }
        assertEquals(0, errorCount)

        // Stop
        wrapper.stop()
        verify(exactly = 1) { mainUpdater.stop() }
        verify(exactly = 1) { fallbackUpdater.stop() }
        assertEquals(0, errorCount)

        // Start again
        wrapper.start { errorCount++ }
        verify(exactly = 2) { mainUpdater.start(any()) }
        verify(exactly = 0) { fallbackUpdater.start() }
        assertEquals(0, errorCount)

        // Shutdown
        wrapper.shutdown()
        verify(exactly = 1) { mainUpdater.shutdown() }
        verify(exactly = 1) { mainUpdater.shutdown() }
    }

    @Test
    fun `Test FallbackRetryWrapper main success no fallback updater`() {
        val wrapper = FlagConfigFallbackRetryWrapper(mainUpdater, null, 1000, 0)
        var errorCount = 0

        // Main starts
        wrapper.start { errorCount++ }
        verify(exactly = 1) { mainUpdater.start(any()) }
        assertEquals(0, errorCount)

        // Stop
        wrapper.stop()
        verify(exactly = 1) { mainUpdater.stop() }
        assertEquals(0, errorCount)

        // Start again
        wrapper.start { errorCount++ }
        verify(exactly = 2) { mainUpdater.start(any()) }
        assertEquals(0, errorCount)

        // Shutdown
        wrapper.shutdown()
        verify(exactly = 1) { mainUpdater.shutdown() }
    }

    @Test
    fun `Test FallbackRetryWrapper main start error and retries with no fallback updater`() {
        val wrapper = FlagConfigFallbackRetryWrapper(mainUpdater, null, 1000, 0)
        var errorCount = 0

        every { mainUpdater.start(capture(mainOnErrorCapture)) } answers { throw Error() }

        // Main start fail, no error, same as success case
        wrapper.start { errorCount++ }
        verify(exactly = 1) { mainUpdater.start(any()) }
        assertEquals(0, errorCount)

        // Retries start
        Thread.sleep(1100)
        verify(exactly = 2) { mainUpdater.start(any()) }
        assertEquals(0, errorCount)

        wrapper.shutdown()
    }

    @Test
    fun `Test FallbackRetryWrapper main error callback and retries with no fallback updater`() {
        val wrapper = FlagConfigFallbackRetryWrapper(mainUpdater, null, 1000, 0)
        var errorCount = 0

        // Main start success
        wrapper.start { errorCount++ }
        verify(exactly = 1) { mainUpdater.start(any()) }
        assertEquals(0, errorCount)

        // Signal error
        mainOnErrorCapture.captured()
        verify(exactly = 1) { mainUpdater.start(any()) }
        assertEquals(1, errorCount) // Updater failure from success calls callback

        // Retry fail after 1s
        every { mainUpdater.start(capture(mainOnErrorCapture)) } answers { throw Error() }
        Thread.sleep(1100)
        verify(exactly = 2) { mainUpdater.start(any()) }
        assertEquals(1, errorCount) // Updater restart error doesn't call callback

        // Retry success after 1s
        justRun { mainUpdater.start(capture(mainOnErrorCapture)) }
        Thread.sleep(1100)
        verify(exactly = 3) { mainUpdater.start(any()) }
        assertEquals(1, errorCount)

        // No more start
        Thread.sleep(1100)
        verify(exactly = 3) { mainUpdater.start(any()) }
        assertEquals(1, errorCount)

        wrapper.shutdown()
    }

    @Test
    fun `Test FallbackRetryWrapper main error callback and retries`() {
//        val wrapper = FlagConfigFallbackRetryWrapper(mainUpdater, fallbackUpdater, 1000, 0)
//        var errorCount = 0
//
//        // Main start success
//        wrapper.start { errorCount++ }
//        verify(exactly = 1) { mainUpdater.start(any()) }
//        verify(exactly = 0) { fallbackUpdater.start(any()) }
//        assertEquals(0, errorCount)
//
//        // Signal error
//        mainOnErrorCapture.captured()
//        verify(exactly = 1) { mainUpdater.start(any()) }
//        verify(exactly = 1) { fallbackUpdater.start(any()) }
//        assertEquals(0, errorCount) // Fallback succeeded, so no callback
//
//        // Retry fail after 1s
//        every { mainUpdater.start(capture(mainOnErrorCapture)) } answers { throw Error() }
//        Thread.sleep(1100)
//        verify(exactly = 2) { mainUpdater.start(any()) }
//        verify(exactly = 1) { fallbackUpdater.start(any()) }
//        assertEquals(0, errorCount)
//
//        // Signal fallback fails
//        fallbackOnErrorCapture.captured()
//
//
//        // Retry success after 1s
//        justRun { mainUpdater.start(capture(mainOnErrorCapture)) }
//        Thread.sleep(1100)
//        verify(exactly = 3) { mainUpdater.start(any()) }
//        assertEquals(1, errorCount)
//
//        // No more start
//        Thread.sleep(1100)
//        verify(exactly = 3) { mainUpdater.start(any()) }
//        assertEquals(1, errorCount)
//
//        wrapper.shutdown()
    }
}