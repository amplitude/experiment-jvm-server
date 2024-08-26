package com.amplitude.experiment.util

import com.amplitude.experiment.LIBRARY_VERSION
import okhttp3.HttpUrl
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
import kotlin.concurrent.schedule

internal class StreamException(error: String): Throwable(error)

private const val KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT = 0L // no timeout
private const val RECONN_INTERVAL_MILLIS_DEFAULT = 30 * 60 * 1000L
private const val MAX_JITTER_MILLIS_DEFAULT = 5000L
internal class SseStream (
    private val authToken: String,
    private val url: HttpUrl,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val connectionTimeoutMillis: Long,
    private val keepaliveTimeoutMillis: Long = KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT,
    private val reconnIntervalMillis: Long = RECONN_INTERVAL_MILLIS_DEFAULT,
    private val maxJitterMillis: Long = MAX_JITTER_MILLIS_DEFAULT
) {
    private val reconnIntervalRange = (reconnIntervalMillis - maxJitterMillis)..(reconnIntervalMillis + maxJitterMillis)
    private val eventSourceListener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            // No action needed.
        }

        override fun onClosed(eventSource: EventSource) {
            if ((eventSource != es)) {
                // Not the current event source using right now, should cancel.
                eventSource.cancel()
                return
            }
            // Server closed the connection, just reconnect.
            cancel()
            connect()
        }

        override fun onEvent(
            eventSource: EventSource,
            id: String?,
            type: String?,
            data: String
        ) {
            if ((eventSource != es)) {
                // Not the current event source using right now, should cancel.
                eventSource.cancel()
                return
            }
            // Keep alive data
            if (" " == data) {
                return
            }
            onUpdate?.let { it(data) }
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if ((eventSource != es)) {
                // Not the current event source using right now, should cancel.
                eventSource.cancel()
                return
            }
            if (t is StreamResetException && t.errorCode == ErrorCode.CANCEL) {
                // Relying on okhttp3.internal to differentiate cancel case.
                // Can be a pitfall later on.
                return
            }
            cancel()
            var err = t
            if (t == null) {
                err = if (response != null) {
                    StreamException(response.toString())
                } else {
                    StreamException("Unknown stream failure")
                }
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

    private var es: EventSource? = null
    private var reconnectTimerTask: TimerTask? = null
    var onUpdate: ((String) -> Unit)? = null
    var onError: ((Throwable?) -> Unit)? = null

    fun connect() {
        cancel() // Clear any existing event sources.
        es = EventSources.createFactory(client).newEventSource(request = request, listener = eventSourceListener)
        reconnectTimerTask = Timer().schedule(reconnIntervalRange.random()) {// Timer for a new event source.
            // This forces client side reconnection after interval.
            this@SseStream.cancel()
            connect()
        }
    }

    fun cancel() {
        reconnectTimerTask?.cancel()

        // There can be cases where an event source is being cancelled by these calls, but take a long time and made a callback to onFailure callback.
        es?.cancel()
        es = null
    }
}