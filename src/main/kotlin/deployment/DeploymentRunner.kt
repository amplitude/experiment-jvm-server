package com.amplitude.experiment.deployment

import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.cohort.CohortApi
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigFallbackRetryWrapper
import com.amplitude.experiment.flag.FlagConfigPoller
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.flag.FlagConfigStreamApi
import com.amplitude.experiment.flag.FlagConfigStreamer
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.daemonFactory
import com.amplitude.experiment.util.getAllCohortIds
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val MIN_COHORT_POLLING_INTERVAL = 60000L
private const val FLAG_STREAMING_RETRY_DELAY = 15000L
private const val FLAG_RETRY_JITTER = 1000L

internal class DeploymentRunner(
    private val config: LocalEvaluationConfig,
    private val flagConfigApi: FlagConfigApi,
    private val flagConfigProxyApi: FlagConfigApi? = null,
    private val flagConfigStreamApi: FlagConfigStreamApi? = null,
    private val flagConfigStorage: FlagConfigStorage,
    cohortApi: CohortApi?,
    private val cohortStorage: CohortStorage?,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) {
    private val startLock = Once()
    private val stopLock = Once()
    private val poller = Executors.newScheduledThreadPool(1, daemonFactory)
    private val cohortLoader = if (cohortApi != null && cohortStorage != null) {
        CohortLoader(cohortApi, cohortStorage, metrics)
    } else {
        null
    }
    private val cohortPollingInterval: Long = getCohortPollingInterval()
    // Fallback in this order: proxy, stream, poll.
    private val amplitudeFlagConfigPoller = FlagConfigFallbackRetryWrapper(
        FlagConfigPoller(flagConfigApi, flagConfigStorage, cohortLoader, cohortStorage, config, metrics),
        null,
        config.flagConfigPollerIntervalMillis,
    )
    private val amplitudeFlagConfigUpdater =
        if (flagConfigStreamApi != null)
            FlagConfigFallbackRetryWrapper(
                FlagConfigStreamer(flagConfigStreamApi, flagConfigStorage, cohortLoader, cohortStorage, metrics),
                amplitudeFlagConfigPoller,
                FLAG_STREAMING_RETRY_DELAY,
                FLAG_RETRY_JITTER,
                config.flagConfigPollerIntervalMillis,
                0,
            )
        else amplitudeFlagConfigPoller
    private val flagConfigUpdater =
        if (flagConfigProxyApi != null)
            FlagConfigFallbackRetryWrapper(
                FlagConfigPoller(flagConfigProxyApi, flagConfigStorage, cohortLoader, cohortStorage, config, metrics),
                amplitudeFlagConfigPoller,
                config.flagConfigPollerIntervalMillis,
                0,
                if (flagConfigStreamApi != null) FLAG_STREAMING_RETRY_DELAY else config.flagConfigPollerIntervalMillis,
                if (flagConfigStreamApi != null) FLAG_RETRY_JITTER else 0,
            )
        else
            amplitudeFlagConfigUpdater

    fun start() = startLock.once {
        flagConfigUpdater.start()
        if (cohortLoader != null) {
            poller.scheduleWithFixedDelay(
                {
                    try {
                        val cohortIds = flagConfigStorage.getFlagConfigs().values.getAllCohortIds()
                        for (cohortId in cohortIds) {
                            cohortLoader.loadCohort(cohortId).handle { _, exception ->
                                if (exception != null) {
                                    Logger.e("Failed to load cohort $cohortId", exception)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Logger.e("Refresh cohorts failed.", t)
                    }
                },
                cohortPollingInterval,
                cohortPollingInterval,
                TimeUnit.MILLISECONDS
            )
        }
    }

    fun stop() {
        if (!startLock.done) return
        stopLock.once {
            poller.shutdown()
            flagConfigUpdater.shutdown()
        }
    }

    private fun getCohortPollingInterval(): Long {
        if (config.cohortSyncConfig == null ||
            config.cohortSyncConfig.cohortPollingIntervalMillis < MIN_COHORT_POLLING_INTERVAL
        ) {
            return MIN_COHORT_POLLING_INTERVAL
        }
        return config.cohortSyncConfig.cohortPollingIntervalMillis
    }
}
