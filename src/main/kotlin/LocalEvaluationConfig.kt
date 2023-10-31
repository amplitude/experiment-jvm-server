@file:OptIn(ExperimentalApi::class)

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
    @JvmField
    val cohortSyncConfiguration: CohortSyncConfiguration? = Defaults.COHORT_SYNC_CONFIGURATION,
    @JvmField
    val proxyConfiguration: ProxyConfiguration? = Defaults.PROXY_CONFIGURATION,
    @JvmField
    val assignmentConfiguration: AssignmentConfiguration? = Defaults.ASSIGNMENT_CONFIGURATION,
    @JvmField
    val metrics: LocalEvaluationMetrics? = Defaults.LOCAL_EVALUATION_METRICS,
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

        /**
         * null
         */
        val COHORT_SYNC_CONFIGURATION: CohortSyncConfiguration? = null

        /**
         * null
         */
        val PROXY_CONFIGURATION: ProxyConfiguration? = null

        /**
         * null
         */
        val ASSIGNMENT_CONFIGURATION: AssignmentConfiguration? = null

        /**
         * null
         */
        val LOCAL_EVALUATION_METRICS: LocalEvaluationMetrics? = null
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
        private var cohortSyncConfiguration = Defaults.COHORT_SYNC_CONFIGURATION
        private var proxyConfiguration = Defaults.PROXY_CONFIGURATION
        private var assignmentConfiguration = Defaults.ASSIGNMENT_CONFIGURATION
        private var metrics = Defaults.LOCAL_EVALUATION_METRICS

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

        @ExperimentalApi
        fun enableCohortSync(cohortSyncConfiguration: CohortSyncConfiguration) = apply {
            this.cohortSyncConfiguration = cohortSyncConfiguration
        }

        @ExperimentalApi
        fun enableProxyMode(proxyConfiguration: ProxyConfiguration) = apply {
            this.proxyConfiguration = proxyConfiguration
        }

        @ExperimentalApi
        fun enableAssignmentTracking(assignmentConfiguration: AssignmentConfiguration) = apply {
            this.assignmentConfiguration = assignmentConfiguration
        }

        @ExperimentalApi
        fun enableMetrics(metrics: LocalEvaluationMetrics) = apply {
            this.metrics = metrics
        }

        fun build(): LocalEvaluationConfig {
            return LocalEvaluationConfig(
                debug = debug,
                serverUrl = serverUrl,
                flagConfigPollerIntervalMillis = flagConfigPollerIntervalMillis,
                flagConfigPollerRequestTimeoutMillis = flagConfigPollerRequestTimeoutMillis,
                cohortSyncConfiguration = cohortSyncConfiguration,
                proxyConfiguration = proxyConfiguration,
                assignmentConfiguration = assignmentConfiguration,
                metrics = metrics,
            )
        }
    }

    override fun toString(): String {
        return "ExperimentConfig(debug=$debug, serverUrl='$serverUrl', " +
            "flagConfigPollerIntervalMillis=$flagConfigPollerIntervalMillis, " +
            "flagConfigPollerRequestTimeoutMillis=$flagConfigPollerRequestTimeoutMillis)"
    }
}

@ExperimentalApi
data class CohortSyncConfiguration(
    val apiKey: String,
    val secretKey: String,
    val maxCohortSize: Int = 15000,
)

@ExperimentalApi
data class AssignmentConfiguration(
    val apiKey: String,
    val filterCapacity: Int = 65536,
    val eventUploadThreshold: Int = 10,
    val eventUploadPeriodMillis: Int = 10000,
    val useBatchMode: Boolean = true,
)

@ExperimentalApi
data class ProxyConfiguration(
    val proxyUrl: String,
    val cohortCacheCapacity: Int = 15000,
    val cohortCacheTtlMillis: Long = 60000L,
)

@ExperimentalApi
interface LocalEvaluationMetrics {
    fun onEvaluation()
    fun onEvaluationFailure(exception: Exception)
    fun onAssignment()
    fun onAssignmentFilter()
    fun onAssignmentEvent()
    fun onAssignmentEventFailure(exception: Exception)
    fun onFlagConfigFetch()
    fun onFlagConfigFetchFailure(exception: Exception)
    fun onFlagConfigFetchOriginFallback(exception: Exception)
    fun onCohortDescriptionsFetch()
    fun onCohortDescriptionsFetchFailure(exception: Exception)
    fun onCohortDescriptionsFetchOriginFallback(exception: Exception)
    fun onCohortDownload()
    fun onCohortDownloadFailure(exception: Exception)
    fun onCohortDownloadOriginFallback(exception: Exception)
    fun onCohortMembership()
    fun onCohortMembershipFailure(exception: Exception)
}
