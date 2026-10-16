package com.amplitude.experiment

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RemoteEvaluationTransportTest {
    private val user = ExperimentUser(userId = "transport-test")
    private val body = """{"flag":{"key":"on","value":"on"}}"""

    @Test
    fun `default client enables http2 health checks`() {
        val client = RemoteEvaluationClient("test-key")
        val field = RemoteEvaluationClient::class.java.getDeclaredField("httpClient")
        field.isAccessible = true
        val transport = field.get(client) as OkHttpClient
        assertEquals(10_000, transport.pingIntervalMillis)
        assertTrue(Protocol.HTTP_2 in transport.protocols)
    }

    @Test
    fun `queued attempts time out and are canceled before dispatcher admission`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody(body))
            server.start()
            val dispatcher = Dispatcher().apply {
                maxRequests = 1
                maxRequestsPerHost = 1
            }
            val transport = OkHttpClient.Builder().dispatcher(dispatcher).readTimeout(0, TimeUnit.MILLISECONDS).build()
            try {
                val blocker = transport.newCall(Request.Builder().url(server.url("/block")).build())
                val blockerFinished = CountDownLatch(1)
                blocker.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        blockerFinished.countDown()
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        blockerFinished.countDown()
                    }
                })
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                val client = client(server, transport, timeoutMillis = 150, retries = 1)
                val failure = assertFailsWith<ExecutionException> { client.fetch(user).get(2, TimeUnit.SECONDS) }
                assertTrue(failure.cause is IOException)
                // Both the initial attempt and its retry expired while another call held the slot.
                assertEquals(2, dispatcher.queuedCallsCount())
                assertTrue(dispatcher.queuedCalls().all { it.isCanceled() })
                assertEquals(1, server.requestCount)
                blocker.cancel()
                assertTrue(blockerFinished.await(2, TimeUnit.SECONDS))
                assertEquals("on", client.fetch(user).get(2, TimeUnit.SECONDS)["flag"]?.value)
                assertEquals(2, server.requestCount)
            } finally {
                close(transport)
            }
        }
    }

    @Test
    fun `application continuations cannot block other queued attempt deadlines`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val dispatcher = Dispatcher().apply { maxRequestsPerHost = 1 }
            val transport = OkHttpClient.Builder().dispatcher(dispatcher).readTimeout(0, TimeUnit.MILLISECONDS).build()
            // Exhaust the old common-pool completion path, not just one worker.
            val continuationCount = maxOf(2, ForkJoinPool.getCommonPoolParallelism())
            val continuationStarted = CountDownLatch(continuationCount)
            val releaseContinuation = CountDownLatch(1)
            try {
                transport.newCall(Request.Builder().url(server.url("/block")).build()).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}

                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                    }
                })
                assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                val client = client(server, transport, timeoutMillis = 150)
                repeat(continuationCount) {
                    client.fetch(user).whenComplete { _, _ ->
                        continuationStarted.countDown()
                        releaseContinuation.await(10, TimeUnit.SECONDS)
                    }
                }
                assertTrue(continuationStarted.await(2, TimeUnit.SECONDS))
                val retryingClient = client(server, transport, timeoutMillis = 150, retries = 1)
                assertFailsWith<ExecutionException> { retryingClient.fetch(user).get(2, TimeUnit.SECONDS) }
                assertEquals(continuationCount + 2, dispatcher.queuedCallsCount())
                assertTrue(dispatcher.queuedCalls().all { it.isCanceled() })
            } finally {
                releaseContinuation.countDown()
                close(transport)
            }
        }
    }

    @Test
    fun `http2 ping failure retires a silently stalled connection`() {
        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
            server.enqueue(MockResponse().setBody(body))
            server.enqueue(MockResponse().setBody(body))
            server.start()
            BlackholeProxy(server.port).use { proxy ->
                val transport = OkHttpClient.Builder()
                    .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                    // Accelerate the SDK default's ping policy for this regression test.
                    .pingInterval(100, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(false)
                    .build()
                try {
                    val client = RemoteEvaluationClient(
                        "test-key",
                        RemoteEvaluationConfig.builder()
                            .serverUrl("http://127.0.0.1:${proxy.port}")
                            .httpClient(transport)
                            .fetchTimeoutMillis(500)
                            .fetchRetries(0)
                            .build()
                    )
                    assertEquals("on", client.fetch(user).get(3, TimeUnit.SECONDS)["flag"]?.value)
                    assertEquals(0, server.takeRequest(2, TimeUnit.SECONDS)?.sequenceNumber)
                    val stalled = proxy.connections.single()
                    // Neither peer receives FIN/RST: bytes on the established TCP flow disappear.
                    stalled.dropTraffic.set(true)
                    val failure = assertFailsWith<ExecutionException> { client.fetch(user).get(2, TimeUnit.SECONDS) }
                    assertTrue(failure.cause is IOException)
                    assertTrue(stalled.closed.await(3, TimeUnit.SECONDS), "ping failure should close the dead connection")
                    assertEquals("on", client.fetch(user).get(3, TimeUnit.SECONDS)["flag"]?.value)
                    assertEquals(2, proxy.connections.size)
                    // Sequence zero confirms a fresh HTTP/2 connection reached the server.
                    assertEquals(0, server.takeRequest(2, TimeUnit.SECONDS)?.sequenceNumber)
                } finally {
                    close(transport)
                }
            }
        }
    }

    @Test
    fun `custom transport and successful retries preserve request behavior`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setBody(body))
            server.start()
            val transport = OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Custom-Transport", "yes").build())
            }.build()
            try {
                val variants = client(server, transport, retries = 1).fetch(
                    user, FetchOptions(tracksAssignment = false, tracksExposure = true)
                ).get(3, TimeUnit.SECONDS)
                assertEquals("on", variants["flag"]?.value)
                repeat(2) {
                    val request = assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
                    assertEquals("/sdk/v2/vardata?v=0", request.path)
                    assertEquals("Api-Key test-key", request.getHeader("Authorization"))
                    assertEquals("yes", request.getHeader("Custom-Transport"))
                    assertEquals("no-track", request.getHeader("X-Amp-Exp-Track"))
                    assertEquals("track", request.getHeader("X-Amp-Exp-Exposure-Track"))
                }
                // Caller-owned resources remain usable after SDK calls complete.
                server.enqueue(MockResponse().setBody("shared"))
                transport.newCall(Request.Builder().url(server.url("/shared")).build()).execute().use {
                    assertEquals("shared", it.body?.string())
                }
            } finally {
                close(transport)
            }
        }
    }

    @Test
    fun `explicit proxy overrides the supplied client proxy`() {
        MockWebServer().use { proxy ->
            proxy.enqueue(MockResponse().setBody(body))
            proxy.start()
            val transport = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).build()
            try {
                val config = RemoteEvaluationConfig.builder()
                    .serverUrl("http://unresolvable.invalid")
                    .httpClient(transport)
                    .httpProxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                    .fetchRetries(0)
                    .fetchTimeoutMillis(2000)
                    .build()
                assertSame(transport, config.httpClient)
                assertEquals("on", RemoteEvaluationClient("test-key", config).fetch(user).get(3, TimeUnit.SECONDS)["flag"]?.value)
                assertTrue(assertNotNull(proxy.takeRequest(2, TimeUnit.SECONDS)).requestLine.startsWith("GET http://unresolvable.invalid/"))
            } finally {
                close(transport)
            }
        }
    }

    @Test
    fun `zero timeout permits a slow response and overrides custom call timeout`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(body).setBodyDelay(200, TimeUnit.MILLISECONDS))
            server.start()
            val transport = OkHttpClient.Builder().callTimeout(1, TimeUnit.MILLISECONDS).build()
            try {
                assertEquals("on", client(server, transport, timeoutMillis = 0).fetch(user).get(3, TimeUnit.SECONDS)["flag"]?.value)
            } finally {
                close(transport)
            }
        }
    }

    @Test
    fun `malformed responses fail the future even with no timeout`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("invalid json"))
            server.start()
            val transport = OkHttpClient()
            try {
                assertFailsWith<ExecutionException> { client(server, transport, timeoutMillis = 0).fetch(user).get(3, TimeUnit.SECONDS) }
            } finally {
                close(transport)
            }
        }
    }

    private fun client(server: MockWebServer, transport: OkHttpClient, timeoutMillis: Long = 2000, retries: Int = 0) =
        RemoteEvaluationClient(
            "test-key",
            RemoteEvaluationConfig.builder().serverUrl(server.url("/").toString())
                .httpClient(transport).fetchTimeoutMillis(timeoutMillis).fetchRetries(retries).build()
        )

    private fun close(transport: OkHttpClient) {
        transport.dispatcher.cancelAll()
        transport.connectionPool.evictAll()
        transport.dispatcher.executorService.shutdownNow()
    }
}

/** Forwards TCP until a connection is blackholed, without closing either peer's socket. */
private class BlackholeProxy(private val upstreamPort: Int) : Closeable {
    private val listener = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
    val port: Int = listener.localPort
    val connections = CopyOnWriteArrayList<Connection>()

    init {
        executor.execute {
            try {
                while (!listener.isClosed) {
                    val downstream = listener.accept()
                    val upstream = Socket(InetAddress.getLoopbackAddress(), upstreamPort)
                    val connection = Connection(downstream, upstream)
                    connections.add(connection)
                    executor.execute { connection.pipe(downstream, upstream) }
                    executor.execute { connection.pipe(upstream, downstream) }
                }
            } catch (_: IOException) {
                // Closing the listener ends the accept loop.
            }
        }
    }

    override fun close() {
        listener.close()
        connections.forEach { it.close() }
        executor.shutdownNow()
    }

    class Connection(private val downstream: Socket, private val upstream: Socket) : Closeable {
        val dropTraffic = AtomicBoolean(false)
        val closed = CountDownLatch(1)

        fun pipe(source: Socket, destination: Socket) {
            try {
                val buffer = ByteArray(8192)
                val input = source.getInputStream()
                val output = destination.getOutputStream()
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (!dropTraffic.get()) {
                        output.write(buffer, 0, count)
                        output.flush()
                    }
                }
            } catch (_: IOException) {
                // The client closes the dead connection after a missed ping.
            } finally {
                close()
            }
        }

        override fun close() {
            downstream.close()
            upstream.close()
            closed.countDown()
        }
    }
}
