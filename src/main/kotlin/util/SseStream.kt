package com.amplitude.experiment.util

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

internal class StreamException(error: String) : Throwable(error)

private const val KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT = 0L // no timeout
private const val RECONN_INTERVAL_MILLIS_DEFAULT = 30 * 60 * 1000L
private const val MAX_JITTER_MILLIS_DEFAULT = 5000L
private const val KEEP_ALIVE_DATA = " "

/**
 * For establishing an SSE stream.
 */
internal class SseStream(
    authToken: String, // Will be used in header as Authorization: <authToken>
    url: HttpUrl, // The full url to connect to.
    httpClient: OkHttpClient = OkHttpClient(),
    connectionTimeoutMillis: Long, // Timeout for establishing a connection, not including reading body.
    keepaliveTimeoutMillis: Long = KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT, // Keep alive should receive within this timeout.
    reconnIntervalMillis: Long = RECONN_INTERVAL_MILLIS_DEFAULT, // Reconnect every this interval.
    maxJitterMillis: Long = MAX_JITTER_MILLIS_DEFAULT, // Jitter for reconnection.
) {
    private val lock: ReentrantLock = ReentrantLock()
    private val reconnIntervalRange = max(0, reconnIntervalMillis - maxJitterMillis)..(min(reconnIntervalMillis, Long.MAX_VALUE - maxJitterMillis) + maxJitterMillis)

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
    private var onUpdate: ((String) -> Unit)? = null
    private var onError: ((Throwable?) -> Unit)? = null

    private val eventSourceListener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            // No action needed.
        }

        override fun onClosed(eventSource: EventSource) {
            lock.withLock {
                if ((eventSource != es)) { // Reference comparison.
                    // Not the current event source using right now, should cancel.
                    eventSource.cancel()
                    return
                }
                // Server closed the connection, just reconnect.
                cancelSse()
            }
            connect(onUpdate, onError)
        }

        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            lock.withLock {
                if ((eventSource != es)) { // Reference comparison.
                    // Not the current event source using right now, should cancel.
                    eventSource.cancel()
                    return
                }
            }
            // Keep alive data
            if (KEEP_ALIVE_DATA == data) {
                return
            }
            onUpdate?.invoke(data)
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            lock.withLock {
                if ((eventSource != es)) { // Reference comparison.
                    // Not the current event source using right now, should cancel.
                    eventSource.cancel()
                    return
                }
                if (t is StreamResetException && t.errorCode == ErrorCode.CANCEL) {
                    // Relying on okhttp3.internal to differentiate cancel case.
                    // Can be a pitfall later on.
                    return
                }
                cancelSse()
            }
            val err = t
                ?: if (response != null) {
                    StreamException(response.toString())
                } else {
                    StreamException("Unknown stream failure")
                }
            onError?.invoke(err)
        }
    }

    /**
     * Creates an event source and immediately returns. The connection is performed async. All errors are informed through callbacks.
     * This the call may success even if stream cannot be established.
     *
     * @param onUpdate On stream update, this callback will be called.
     * @param onError On stream error, this callback will be called.
     */
    internal fun connect(onUpdate: ((String) -> Unit)?, onError: ((Throwable?) -> Unit)?) {
        lock.withLock {
            cancelSse() // Clear any existing event sources.

            this.onUpdate = onUpdate
            this.onError = onError
            es = client.newEventSource(request, eventSourceListener)
            reconnectTimerTask = Timer().schedule(reconnIntervalRange.random()) { // Timer for a new event source.
                // This forces client side reconnection after interval.
                this@SseStream.cancel()
                connect(onUpdate, onError)
            }
        }
    }

    // @GuardedBy(lock)
    private fun cancelSse() {
        reconnectTimerTask?.cancel()

        // There can be cases where an event source is being cancelled by these calls, but take a long time and made a callback to onFailure callback.
        es?.cancel()
        es = null
    }

    /**
     * Cancels the current connection.
     */
    internal fun cancel() {
        lock.withLock {
            cancelSse()
            this.onUpdate = null
            this.onError = null
        }
    }
}
