package com.amplitude.experiment

import com.amplitude.Middleware
import com.amplitude.experiment.LocalEvaluationConfig.Defaults

/**
 * Configuration options. This is an immutable object that can be created using
 * a [LocalEvaluationConfig.Builder]. Example usage:
 *
 * `LocalEvaluationConfig.builder().serverUrl("https://api.lab.amplitude.com/").build()`
 */
@OptIn(ExperimentalApi::class)
class LocalEvaluationConfig internal constructor(
    @JvmField
    val debug: Boolean = Defaults.DEBUG,
    @JvmField
    val serverUrl: String = Defaults.SERVER_URL,
    @JvmField
    val serverZone: ServerZone = Defaults.SERVER_ZONE,
    @JvmField
    val flagConfigPollerIntervalMillis: Long = Defaults.FLAG_CONFIG_POLLER_INTERVAL_MILLIS,
    @JvmField
    val flagConfigPollerRequestTimeoutMillis: Long = Defaults.FLAG_CONFIG_POLLER_REQUEST_TIMEOUT_MILLIS,
    @JvmField
    val assignmentConfiguration: AssignmentConfiguration? = Defaults.ASSIGNMENT_CONFIGURATION,
    @JvmField
    val cohortSyncConfig: CohortSyncConfig? = Defaults.COHORT_SYNC_CONFIGURATION,
    @JvmField
    val evaluationProxyConfig: EvaluationProxyConfig? = Defaults.EVALUATION_PROXY_CONFIGURATION,
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
        const val SERVER_URL = US_SERVER_URL

        /**
         * "https://cohort-v2.lab.amplitude.com/"
         */
        const val COHORT_SERVER_URL = US_COHORT_SERVER_URL

        /**
         * "https://api2.amplitude.com/2/httpapi"
         */
        const val EVENT_SERVER_URL = US_EVENT_SERVER_URL

        /**
         * ServerZone.US
         */
        val SERVER_ZONE = ServerZone.US

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
        val ASSIGNMENT_CONFIGURATION: AssignmentConfiguration? = null

        /**
         * null
         */
        val COHORT_SYNC_CONFIGURATION: CohortSyncConfig? = null

        /**
         * null
         */
        val EVALUATION_PROXY_CONFIGURATION: EvaluationProxyConfig? = null

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
        private var serverZone = Defaults.SERVER_ZONE
        private var serverUrl = Defaults.SERVER_URL
        private var flagConfigPollerIntervalMillis = Defaults.FLAG_CONFIG_POLLER_INTERVAL_MILLIS
        private var flagConfigPollerRequestTimeoutMillis = Defaults.FLAG_CONFIG_POLLER_REQUEST_TIMEOUT_MILLIS
        private var assignmentConfiguration = Defaults.ASSIGNMENT_CONFIGURATION
        private var cohortSyncConfiguration = Defaults.COHORT_SYNC_CONFIGURATION
        private var evaluationProxyConfiguration = Defaults.EVALUATION_PROXY_CONFIGURATION
        private var metrics = Defaults.LOCAL_EVALUATION_METRICS

        fun debug(debug: Boolean) = apply {
            this.debug = debug
        }

        fun serverUrl(serverUrl: String) = apply {
            this.serverUrl = serverUrl
        }

        fun serverZone(serverZone: ServerZone) = apply {
            this.serverZone = serverZone
        }

        fun flagConfigPollerIntervalMillis(flagConfigPollerIntervalMillis: Long) = apply {
            this.flagConfigPollerIntervalMillis = flagConfigPollerIntervalMillis
        }

        fun flagConfigPollerRequestTimeoutMillis(flagConfigPollerRequestTimeoutMillis: Long) = apply {
            this.flagConfigPollerRequestTimeoutMillis = flagConfigPollerRequestTimeoutMillis
        }

        fun enableAssignmentTracking(assignmentConfiguration: AssignmentConfiguration) = apply {
            this.assignmentConfiguration = assignmentConfiguration
        }

        fun cohortSyncConfig(cohortSyncConfig: CohortSyncConfig) = apply {
            this.cohortSyncConfiguration = cohortSyncConfig
        }

        @ExperimentalApi
        fun evaluationProxyConfig(evaluationProxyConfig: EvaluationProxyConfig) = apply {
            this.evaluationProxyConfiguration = evaluationProxyConfig
        }

        @ExperimentalApi
        fun metrics(metrics: LocalEvaluationMetrics) = apply {
            this.metrics = metrics
        }

        fun build(): LocalEvaluationConfig {
            return LocalEvaluationConfig(
                debug = debug,
                serverUrl = serverUrl,
                serverZone = serverZone,
                flagConfigPollerIntervalMillis = flagConfigPollerIntervalMillis,
                flagConfigPollerRequestTimeoutMillis = flagConfigPollerRequestTimeoutMillis,
                assignmentConfiguration = assignmentConfiguration,
                cohortSyncConfig = cohortSyncConfiguration,
                evaluationProxyConfig = evaluationProxyConfiguration,
                metrics = metrics,
            )
        }
    }
}

data class AssignmentConfiguration @JvmOverloads constructor(
    val apiKey: String,
    val cacheCapacity: Int = 65536,
    val eventUploadThreshold: Int = 10,
    val eventUploadPeriodMillis: Int = 10000,
    val useBatchMode: Boolean = true,
    val serverUrl: String = Defaults.EVENT_SERVER_URL,
    val middleware: List<Middleware> = listOf(),
)

@ExperimentalApi
data class CohortSyncConfig @JvmOverloads constructor(
    val apiKey: String,
    val secretKey: String,
    val maxCohortSize: Int = Int.MAX_VALUE,
    val cohortServerUrl: String = Defaults.COHORT_SERVER_URL,
    val cohortPollingIntervalMillis: Long = 60000L
)

@ExperimentalApi
data class EvaluationProxyConfig @JvmOverloads constructor(
    val proxyUrl: String,
    val cohortCacheCapacity: Int = 1000000,
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
    fun onCohortDownload()
    fun onCohortDownloadFailure(exception: Exception)
    fun onCohortDownloadOriginFallback(exception: Exception)
    fun onProxyCohortMembership()
    fun onProxyCohortMembershipFailure(exception: Exception)
}
