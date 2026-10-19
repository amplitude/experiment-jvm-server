package com.amplitude.experiment

import com.amplitude.experiment.util.DefaultLogger
import com.amplitude.experiment.util.LogLevel
import com.amplitude.experiment.util.LoggerProvider
import okhttp3.OkHttpClient
import java.net.Proxy

/**
 * Configuration options. This is an immutable object that can be created using
 * a [RemoteEvaluationConfig.Builder]. Example usage:
 *
 *`ExperimentConfig.builder().setServerUrl("https://api.lab.amplitude.com/").build()`
 */
class RemoteEvaluationConfig internal constructor(
    @JvmField
    @Deprecated(
        message = "Use logLevel instead",
        replaceWith = ReplaceWith("logLevel")
    )
    val debug: Boolean = Defaults.DEBUG,
    @JvmField
    val logLevel: LogLevel = Defaults.LOG_LEVEL,
    @JvmField
    val loggerProvider: LoggerProvider = Defaults.LOGGER_PROVIDER,
    @JvmField
    val serverUrl: String = Defaults.SERVER_URL,
    @JvmField
    val serverZone: ServerZone = Defaults.SERVER_ZONE,
    @JvmField
    val fetchTimeoutMillis: Long = Defaults.FETCH_TIMEOUT_MILLIS,
    @JvmField
    val fetchRetries: Int = Defaults.FETCH_RETRIES,
    @JvmField
    val fetchRetryBackoffMinMillis: Long = Defaults.FETCH_RETRY_BACKOFF_MIN_MILLIS,
    @JvmField
    val fetchRetryBackoffMaxMillis: Long = Defaults.FETCH_RETRY_BACKOFF_MAX_MILLIS,
    @JvmField
    val fetchRetryBackoffScalar: Double = Defaults.FETCH_RETRY_BACKOFF_SCALAR,
    @JvmField
    val httpProxy: Proxy? = Defaults.HTTP_PROXY,
    /**
     * Optional shared HTTP client. The caller owns its dispatcher and connection pool.
     * An explicit [httpProxy] overrides the client's proxy. Other transport settings,
     * including HTTP/2 pings, are preserved; each fetch attempt uses [fetchTimeoutMillis].
     */
    @JvmField
    val httpClient: OkHttpClient? = null,
) {

    /**
     * Construct the default [RemoteEvaluationConfig].
     */
    constructor() : this(debug = Defaults.DEBUG)

    /**
     * Defaults for [RemoteEvaluationConfig]
     */
    object Defaults {

        /**
         * false
         */
        const val DEBUG = false

        /**
         * ERROR
         */
        val LOG_LEVEL = LogLevel.ERROR

        /**
         * [com.amplitude.experiment.util.DefaultLogger]
         */
        val LOGGER_PROVIDER: LoggerProvider = DefaultLogger()

        /**
         * "https://api.lab.amplitude.com/"
         */
        const val SERVER_URL = US_SERVER_URL

        /**
         * ServerZone.US
         */
        val SERVER_ZONE = ServerZone.US

        /**
         * 500
         */
        const val FETCH_TIMEOUT_MILLIS = 500L

        /**
         * 1
         */
        const val FETCH_RETRIES = 1

        /**
         * 0
         */
        const val FETCH_RETRY_BACKOFF_MIN_MILLIS = 0L

        /**
         * 10000
         */
        const val FETCH_RETRY_BACKOFF_MAX_MILLIS = 10000L

        /**
         * 1
         */
        const val FETCH_RETRY_BACKOFF_SCALAR = 1.0

        /**
         * null
         */
        val HTTP_PROXY: Proxy? = null
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }

    class Builder {

        private var debug = Defaults.DEBUG
        private var logLevel = Defaults.LOG_LEVEL
        private var loggerProvider = Defaults.LOGGER_PROVIDER
        private var serverUrl = Defaults.SERVER_URL
        private var serverZone = Defaults.SERVER_ZONE
        private var fetchTimeoutMillis = Defaults.FETCH_TIMEOUT_MILLIS
        private var fetchRetries = Defaults.FETCH_RETRIES
        private var fetchRetryBackoffMinMillis = Defaults.FETCH_RETRY_BACKOFF_MIN_MILLIS
        private var fetchRetryBackoffMaxMillis = Defaults.FETCH_RETRY_BACKOFF_MAX_MILLIS
        private var fetchRetryBackoffScalar = Defaults.FETCH_RETRY_BACKOFF_SCALAR
        private var httpProxy = Defaults.HTTP_PROXY
        private var httpClient: OkHttpClient? = null

        @Deprecated(
            message = "Use logLevel instead",
            replaceWith = ReplaceWith("logLevel(LogLevel.DEBUG)")
        )
        fun debug(debug: Boolean) = apply {
            this.debug = debug
            if (debug) {
                this.logLevel = LogLevel.DEBUG
            } else {
                this.logLevel = LogLevel.ERROR
            }
        }

        fun logLevel(logLevel: LogLevel) = apply {
            if (this.debug) {
                this.logLevel = LogLevel.DEBUG
            } else {
                this.logLevel = logLevel
            }
        }

        fun loggerProvider(loggerProvider: LoggerProvider) = apply {
            this.loggerProvider = loggerProvider
        }

        fun serverUrl(serverUrl: String) = apply {
            this.serverUrl = serverUrl
        }

        fun serverZone(serverZone: ServerZone) = apply {
            this.serverZone = serverZone
        }

        /**
         * Timeout for each fetch attempt, including dispatcher waiting and response reading.
         * Zero disables the timeout. Retries and their backoff have separate budgets.
         */
        fun fetchTimeoutMillis(fetchTimeoutMillis: Long) = apply {
            this.fetchTimeoutMillis = fetchTimeoutMillis
        }

        fun fetchRetries(fetchRetries: Int) = apply {
            this.fetchRetries = fetchRetries
        }

        fun fetchRetryBackoffMinMillis(fetchRetryBackoffMinMillis: Long) = apply {
            this.fetchRetryBackoffMinMillis = fetchRetryBackoffMinMillis
        }

        fun fetchRetryBackoffMaxMillis(fetchRetryBackoffMaxMillis: Long) = apply {
            this.fetchRetryBackoffMaxMillis = fetchRetryBackoffMaxMillis
        }

        fun fetchRetryBackoffScalar(fetchRetryBackoffScalar: Double) = apply {
            this.fetchRetryBackoffScalar = fetchRetryBackoffScalar
        }

        fun httpProxy(httpProxy: Proxy) = apply {
            this.httpProxy = httpProxy
        }

        /**
         * Use a caller-owned client to configure protocols, HTTP/2 pings, or dispatcher limits.
         * Without one, the SDK enables HTTP/2 pings every 10 seconds.
         */
        fun httpClient(httpClient: OkHttpClient) = apply {
            this.httpClient = httpClient
        }

        fun build(): RemoteEvaluationConfig {
            return RemoteEvaluationConfig(
                debug = debug,
                logLevel = logLevel,
                loggerProvider = loggerProvider,
                serverUrl = serverUrl,
                serverZone = serverZone,
                fetchTimeoutMillis = fetchTimeoutMillis,
                fetchRetries = fetchRetries,
                fetchRetryBackoffMinMillis = fetchRetryBackoffMinMillis,
                fetchRetryBackoffMaxMillis = fetchRetryBackoffMaxMillis,
                fetchRetryBackoffScalar = fetchRetryBackoffScalar,
                httpProxy = httpProxy,
                httpClient = httpClient
            )
        }
    }

    override fun toString(): String {
        return "RemoteEvaluationConfig(debug=$debug, serverUrl='$serverUrl', serverZone=$serverZone, " +
            "fetchTimeoutMillis=$fetchTimeoutMillis, fetchRetries=$fetchRetries, " +
            "fetchRetryBackoffMinMillis=$fetchRetryBackoffMinMillis, " +
            "fetchRetryBackoffMaxMillis=$fetchRetryBackoffMaxMillis, " +
            "fetchRetryBackoffScalar=$fetchRetryBackoffScalar, httpProxy=$httpProxy)"
    }
}
