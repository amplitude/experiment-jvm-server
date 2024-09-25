package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.SseStream
import com.amplitude.experiment.util.StreamException
import com.amplitude.experiment.util.json
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal open class FlagConfigStreamApiError(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)
    constructor(cause: Throwable?) : this(cause?.toString(), cause)
}
internal class FlagConfigStreamApiConnTimeoutError : FlagConfigStreamApiError("Initial connection timed out")
internal class FlagConfigStreamApiDataCorruptError : FlagConfigStreamApiError("Stream data corrupted")
internal class FlagConfigStreamApiStreamError(cause: Throwable?) : FlagConfigStreamApiError("Stream error", cause)

private const val CONNECTION_TIMEOUT_MILLIS_DEFAULT = 1500L
private const val KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT = 17000L // keep alive sends at 15s interval. 2s grace period
private const val RECONN_INTERVAL_MILLIS_DEFAULT = 15 * 60 * 1000L
internal class FlagConfigStreamApi(
    deploymentKey: String,
    serverUrl: HttpUrl,
    httpClient: OkHttpClient = OkHttpClient(),
    val connectionTimeoutMillis: Long = CONNECTION_TIMEOUT_MILLIS_DEFAULT,
    keepaliveTimeoutMillis: Long = KEEP_ALIVE_TIMEOUT_MILLIS_DEFAULT,
    reconnIntervalMillis: Long = RECONN_INTERVAL_MILLIS_DEFAULT,
) {
    private val lock: ReentrantLock = ReentrantLock()
    val url = serverUrl.newBuilder().addPathSegments("sdk/stream/v1/flags").build()
    private val stream: SseStream = SseStream(
        "Api-Key $deploymentKey",
        url,
        httpClient,
        connectionTimeoutMillis,
        keepaliveTimeoutMillis,
        reconnIntervalMillis
    )

    /**
     * Connects to flag configs stream.
     * This will ensure stream connects, first set of flags is received and processed successfully, then returns.
     * If stream fails to connect, first set of flags is not received, or first set of flags did not process successfully, it throws.
     */
    internal fun connect(
        onInitUpdate: ((List<EvaluationFlag>) -> Unit)? = null,
        onUpdate: ((List<EvaluationFlag>) -> Unit)? = null,
        onError: ((Exception?) -> Unit)? = null
    ) {
        // Guarded by lock. Update to callbacks and waits can lead to race conditions.
        lock.withLock {
            val isDuringInit = AtomicBoolean(true)
            val connectTimeoutFuture = CompletableFuture<Unit>()
            val updateTimeoutFuture = CompletableFuture<Unit>()
            val onSseUpdate: ((String) -> Unit) = { data ->
                if (isDuringInit.getAndSet(false)) {
                    // Stream is establishing. First data received.
                    // Resolve timeout.
                    connectTimeoutFuture.complete(Unit)

                    // Make sure valid data.
                    try {
                        val flags = getFlagsFromData(data)

                        try {
                            if (onInitUpdate != null) {
                                onInitUpdate.invoke(flags)
                            } else {
                                onUpdate?.invoke(flags)
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
                            onUpdate?.invoke(flags)
                        } catch (_: Throwable) {
                            // Don't care about application error.
                        }
                    } catch (_: Throwable) {
                        // Stream corrupted. Reconnect.
                        handleError(onError, FlagConfigStreamApiDataCorruptError())
                    }
                }
            }
            val onSseError: ((Throwable?) -> Unit) = { t ->
                if (isDuringInit.getAndSet(false)) {
                    connectTimeoutFuture.completeExceptionally(t)
                    updateTimeoutFuture.completeExceptionally(t)
                } else {
                    handleError(onError, FlagConfigStreamApiStreamError(t))
                }
            }
            stream.connect(onSseUpdate, onSseError)

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

    private fun handleError(onError: ((Exception?) -> Unit)?, e: Exception?) {
        close()
        onError?.invoke(e)
    }
}
