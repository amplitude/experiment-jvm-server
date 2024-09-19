package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.*
import com.amplitude.experiment.util.SseStream
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal open class FlagConfigStreamApiError(message: String?, cause: Throwable?): Exception(message, cause) {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
}
internal class FlagConfigStreamApiConnTimeoutError: FlagConfigStreamApiError("Initial connection timed out")
internal class FlagConfigStreamApiDataCorruptError: FlagConfigStreamApiError("Stream data corrupted")
internal class FlagConfigStreamApiStreamError(cause: Throwable?): FlagConfigStreamApiError("Stream error", cause)

private const val CONNECTION_TIMEOUT_MILLIS_DEFAULT = 1500L
private const val KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT = 17000L // keep alive sends at 15s interval. 2s grace period
private const val RECONN_INTERVAL_MILLIS_DEFAULT = 15 * 60 * 1000L
internal class FlagConfigStreamApi (
    deploymentKey: String,
    serverUrl: HttpUrl,
    httpClient: OkHttpClient = OkHttpClient(),
    val connectionTimeoutMillis: Long = CONNECTION_TIMEOUT_MILLIS_DEFAULT,
    keepaliveTimeoutMillis: Long = KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT,
    reconnIntervalMillis: Long = RECONN_INTERVAL_MILLIS_DEFAULT,
) {
    private val lock: ReentrantLock = ReentrantLock()
    var onInitUpdate: ((List<EvaluationFlag>) -> Unit)? = null
    var onUpdate: ((List<EvaluationFlag>) -> Unit)? = null
    var onError: ((Exception?) -> Unit)? = null
    val url = serverUrl.newBuilder().addPathSegments("sdk/stream/v1/flags").build()
    private val stream: SseStream = SseStream(
        "Api-Key $deploymentKey",
        url,
        httpClient,
        connectionTimeoutMillis,
        keepaliveTimeoutMillis,
        reconnIntervalMillis)

    internal fun connect() {
        // Guarded by lock. Update to callbacks and waits can lead to race conditions.
        lock.withLock {
            val isInit = AtomicBoolean(true)
            val connectTimeoutFuture = CompletableFuture<Unit>()
            val updateTimeoutFuture = CompletableFuture<Unit>()
            stream.onUpdate = { data ->
                if (isInit.getAndSet(false)) {
                    // Stream is establishing. First data received.
                    // Resolve timeout.
                    connectTimeoutFuture.complete(Unit)

                    // Make sure valid data.
                    try {
                        val flags = getFlagsFromData(data)

                        try {
                            if (onInitUpdate != null) {
                                onInitUpdate?.let { it(flags) }
                            } else {
                                onUpdate?.let { it(flags) }
                            }
                            updateTimeoutFuture.complete(Unit)
                        } catch (e: Throwable) {
                            updateTimeoutFuture.completeExceptionally(e)
                        }
                    } catch (_: Throwable) {
                        updateTimeoutFuture.completeExceptionally(FlagConfigStreamApiDataCorruptError())
                    }

                } else {
                    // Stream has already established.
                    // Make sure valid data.
                    try {
                        val flags = getFlagsFromData(data)

                        try {
                            onUpdate?.let { it(flags) }
                        } catch (_: Throwable) {
                            // Don't care about application error.
                        }
                    } catch (_: Throwable) {
                        // Stream corrupted. Reconnect.
                        handleError(FlagConfigStreamApiDataCorruptError())
                    }

                }
            }
            stream.onError = { t ->
                if (isInit.getAndSet(false)) {
                    connectTimeoutFuture.completeExceptionally(t)
                    updateTimeoutFuture.completeExceptionally(t)
                } else {
                    handleError(FlagConfigStreamApiStreamError(t))
                }
            }
            stream.connect()

            val t: Throwable
            try {
                connectTimeoutFuture.get(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
                updateTimeoutFuture.get()
                return
            } catch (e: TimeoutException) {
                // Timeouts should retry
                t = FlagConfigStreamApiConnTimeoutError()
            } catch (e: ExecutionException) {
                val cause = e.cause
                t = if (cause is StreamException) {
                    FlagConfigStreamApiStreamError(cause)
                } else {
                    FlagConfigStreamApiError(e)
                }
            } catch (e: Throwable) {
                t = FlagConfigStreamApiError(e)
            }
            close()
            throw t
        }
    }

    internal fun close() {
        // Not guarded by lock. close() can halt connect().
        stream.cancel()
    }

    private fun getFlagsFromData(data: String): List<EvaluationFlag> {
        return json.decodeFromString<List<EvaluationFlag>>(data)
    }

    private fun handleError(e: Exception?) {
        close()
        onError?.let { it(e) }
    }
}
