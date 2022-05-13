package com.amplitude.experiment

/**
 * Configuration options. This is an immutable object that can be created using
 * a [LocalEvaluationConfig.Builder]. Example usage:
 *
 * `LocalEvaluationConfig.builder().serverUrl("https://api.lab.amplitude.com/").build()`
 */
class LocalEvaluationConfig internal constructor(
    @JvmField
    val debug: Boolean = Defaults.DEBUG,
    @JvmField
    val serverUrl: String = Defaults.SERVER_URL,
    @JvmField
    val flagConfigPollerIntervalMillis: Long = Defaults.FLAG_CONFIG_POLLER_INTERVAL_MILLIS,
    @JvmField
    val flagConfigPollerRequestTimeoutMillis: Long = Defaults.FLAG_CONFIG_POLLER_REQUEST_TIMEOUT_MILLIS,
) {

    /**
     * Construct the default [LocalEvaluationConfig].
     */
    constructor() : this(debug = Defaults.DEBUG)

    /**
     * Defaults for [LocalEvaluationConfig]
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
         * 30,000
         */
        const val FLAG_CONFIG_POLLER_INTERVAL_MILLIS = 30_000L

        /**
         * 10,000
         */
        const val FLAG_CONFIG_POLLER_REQUEST_TIMEOUT_MILLIS = 10_000L
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
        private var flagConfigPollerIntervalMillis = Defaults.FLAG_CONFIG_POLLER_INTERVAL_MILLIS
        private var flagConfigPollerRequestTimeoutMillis = Defaults.FLAG_CONFIG_POLLER_REQUEST_TIMEOUT_MILLIS

        fun debug(debug: Boolean) = apply {
            this.debug = debug
        }

        fun serverUrl(serverUrl: String) = apply {
            this.serverUrl = serverUrl
        }

        fun flagConfigPollerIntervalMillis(flagConfigPollerIntervalMillis: Long) = apply {
            this.flagConfigPollerIntervalMillis = flagConfigPollerIntervalMillis
        }

        fun flagConfigPollerRequestTimeoutMillis(flagConfigPollerRequestTimeoutMillis: Long) = apply {
            this.flagConfigPollerRequestTimeoutMillis = flagConfigPollerRequestTimeoutMillis
        }

        fun build(): LocalEvaluationConfig {
            return LocalEvaluationConfig(
                debug = debug,
                serverUrl = serverUrl,
                flagConfigPollerIntervalMillis = flagConfigPollerIntervalMillis,
                flagConfigPollerRequestTimeoutMillis = flagConfigPollerRequestTimeoutMillis,
            )
        }
    }

    override fun toString(): String {
        return "ExperimentConfig(debug=$debug, serverUrl='$serverUrl', " +
                "flagConfigPollerIntervalMillis=$flagConfigPollerIntervalMillis, " +
                "flagConfigPollerRequestTimeoutMillis=$flagConfigPollerRequestTimeoutMillis)"
    }
}
