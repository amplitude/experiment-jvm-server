package com.amplitude.experiment

/**
 * Configuration options. This is an immutable object that can be created using
 * a [ExperimentConfig.Builder]. Example usage:
 *
 *`ExperimentConfig.builder().setServerUrl("https://api.lab.amplitude.com/").build()`
 */
class ExperimentConfig internal constructor(
    @JvmField
    val debug: Boolean = Defaults.DEBUG,
    @JvmField
    val serverUrl: String = Defaults.SERVER_URL,
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
    val fetchRetryTimeoutMillis: Long = Defaults.FETCH_RETRY_TIMEOUT_MILLIS,
) {

    /**
     * Construct the default [ExperimentConfig].
     */
    constructor() : this(debug = Defaults.DEBUG)

    /**
     * Defaults for [ExperimentConfig]
     */
    object Defaults {

        /**
         * false
         */
        const val DEBUG = false

        /**
         * "https://api.lab.amplitude.com/"
         */
        const val SERVER_URL = "https://api.lab.amplitude.com/"

        /**
         * 10000
         */
        const val FETCH_TIMEOUT_MILLIS = 10000L
        /**
         * 8
         */
        const val FETCH_RETRIES = 8
        /**
         * 500
         */
        const val FETCH_RETRY_BACKOFF_MIN_MILLIS = 500L
        /**
         * 10000
         */
        const val FETCH_RETRY_BACKOFF_MAX_MILLIS = 10000L
        /**
         * 1.5
         */
        const val FETCH_RETRY_BACKOFF_SCALAR= 1.5
        /**
         * 10000
         */
        const val FETCH_RETRY_TIMEOUT_MILLIS = 10000L
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }
    }

    class Builder {

        private var debug = Defaults.DEBUG
        private var serverUrl = Defaults.SERVER_URL
        private var fetchTimeoutMillis = Defaults.FETCH_TIMEOUT_MILLIS
        private var fetchRetries = Defaults.FETCH_RETRIES
        private var fetchRetryBackoffMinMillis = Defaults.FETCH_RETRY_BACKOFF_MIN_MILLIS
        private var fetchRetryBackoffMaxMillis = Defaults.FETCH_RETRY_BACKOFF_MAX_MILLIS
        private var fetchRetryBackoffScalar = Defaults.FETCH_RETRY_BACKOFF_SCALAR
        private var fetchRetryTimeoutMillis = Defaults.FETCH_RETRY_TIMEOUT_MILLIS

        fun debug(debug: Boolean) = apply {
            this.debug = debug
        }

        fun serverUrl(serverUrl: String) = apply {
            this.serverUrl = serverUrl
        }

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

        fun fetchRetryTimeoutMillis(fetchRetryTimeoutMillis: Long) = apply {
            this.fetchRetryTimeoutMillis = fetchRetryTimeoutMillis
        }

        fun build(): ExperimentConfig {
            return ExperimentConfig(
                debug = debug,
                serverUrl = serverUrl,
                fetchTimeoutMillis = fetchTimeoutMillis,
                fetchRetries = fetchRetries,
                fetchRetryBackoffMinMillis = fetchRetryBackoffMinMillis,
                fetchRetryBackoffMaxMillis = fetchRetryBackoffMaxMillis,
                fetchRetryBackoffScalar = fetchRetryBackoffScalar,
                fetchRetryTimeoutMillis = fetchRetryTimeoutMillis,
            )
        }
    }

    override fun toString(): String {
        return "ExperimentConfig(debug=$debug, serverUrl='$serverUrl', fetchTimeoutMillis=$fetchTimeoutMillis, " +
                "fetchRetries=$fetchRetries, fetchRetryBackoffMinMillis=$fetchRetryBackoffMinMillis, " +
                "fetchRetryBackoffMaxMillis=$fetchRetryBackoffMaxMillis, " +
                "fetchRetryBackoffScalar=$fetchRetryBackoffScalar, fetchRetryTimeoutMillis=$fetchRetryTimeoutMillis)"
    }
}
