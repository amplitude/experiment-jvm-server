package com.amplitude.experiment.util

import com.amplitude.experiment.LIBRARY_VERSION
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

internal class StreamException(error: String): Throwable(error)

private const val KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT = 0L // no timeout
private const val RECONN_INTERVAL_MILLIS_DEFAULT = 30 * 60 * 1000L
private const val MAX_JITTER_MILLIS_DEFAULT = 5000L
private const val KEEP_ALIVE_DATA = " "
internal class SseStream (
    private val authToken: String,
    private val url: HttpUrl,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val connectionTimeoutMillis: Long,
    private val keepaliveTimeoutMillis: Long = KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT,
    private val reconnIntervalMillis: Long = RECONN_INTERVAL_MILLIS_DEFAULT,
    private val maxJitterMillis: Long = MAX_JITTER_MILLIS_DEFAULT,
) {
    private val lock: ReentrantLock = ReentrantLock()
    private val reconnIntervalRange = max(0, reconnIntervalMillis - maxJitterMillis)..(min(reconnIntervalMillis, Long.MAX_VALUE - maxJitterMillis) + maxJitterMillis)
    private val eventSourceListener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            // No action needed.
        }

        override fun onClosed(eventSource: EventSource) {
            lock.withLock {
                if ((eventSource != es)) {
                    // Not the current event source using right now, should cancel.
                    eventSource.cancel()
                    return
                }
            }
            // Server closed the connection, just reconnect.
            cancelInternal()
            connect()
        }

        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            lock.withLock {
                if ((eventSource != es)) {
                    // Not the current event source using right now, should cancel.
                    eventSource.cancel()
                    return
                }
            }
            // Keep alive data
            if (KEEP_ALIVE_DATA == data) {
                return
            }
            onUpdate?.let { it(data) }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            lock.withLock {
                if ((eventSource != es)) {
                    // Not the current event source using right now, should cancel.
                    eventSource.cancel()
                    return
                }
            }
            if (t is StreamResetException && t.errorCode == ErrorCode.CANCEL) {
                // Relying on okhttp3.internal to differentiate cancel case.
                // Can be a pitfall later on.
                return
            }
            cancel()
            val err = t
                ?: if (response != null) {
                    StreamException(response.toString())
                } else {
                    StreamException("Unknown stream failure")
                }
            onError?.let { it(err) }
        }
    }

    private val request = newGet(url, null, mapOf("Authorization" to authToken, "Accept" to "text/event-stream"))

    private val client = httpClient.newBuilder() // client.newBuilder reuses the connection pool in the same client with new configs.
        .connectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS) // Connection timeout for establishing SSE.
        .callTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS) // Call timeout for establishing SSE.
        .readTimeout(keepaliveTimeoutMillis, TimeUnit.MILLISECONDS) // Timeout between messages, keepalive in this case.
        .writeTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private var es: EventSource? = null // @GuardedBy(lock)
    private var reconnectTimerTask: TimerTask? = null // @GuardedBy(lock)
    internal var onUpdate: ((String) -> Unit)? = null
    internal var onError: ((Throwable?) -> Unit)? = null

    /**
     * Creates an event source and immediately returns. The connection is performed async. Errors are informed through callbacks.
     */
    internal fun connect() {
        lock.withLock {
            cancelInternal() // Clear any existing event sources.
            es = client.newEventSource(request, eventSourceListener)
            reconnectTimerTask = Timer().schedule(reconnIntervalRange.random()) {// Timer for a new event source.
                // This forces client side reconnection after interval.
                this@SseStream.cancel()
                connect()
            }
        }
    }

    // @GuardedBy(lock)
    private fun cancelInternal() {
        reconnectTimerTask?.cancel()

        // There can be cases where an event source is being cancelled by these calls, but take a long time and made a callback to onFailure callback.
        es?.cancel()
        es = null
    }

    internal fun cancel() {
        lock.withLock {
            cancelInternal()
        }
    }
}