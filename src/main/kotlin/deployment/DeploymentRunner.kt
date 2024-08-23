@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.deployment

import com.amplitude.experiment.*
import com.amplitude.experiment.cohort.CohortApi
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.flag.*
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigPoller
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.daemonFactory
import com.amplitude.experiment.util.getAllCohortIds
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val MIN_COHORT_POLLING_INTERVAL = 60000L

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
    private val lock = Once()
    private val poller = Executors.newScheduledThreadPool(1, daemonFactory)
    private val cohortLoader = if (cohortApi != null && cohortStorage != null) {
        CohortLoader(cohortApi, cohortStorage)
    } else {
        null
    }
    private val cohortPollingInterval: Long = getCohortPollingInterval()
    // Fallback in this order: proxy, stream, poll.
    private val amplitudeFlagConfigPoller = FlagConfigPoller(flagConfigApi, flagConfigStorage, cohortLoader, cohortStorage, config, metrics)
    private val amplitudeFlagConfigUpdater =
        if (flagConfigStreamApi != null)
            FlagConfigFallbackRetryWrapper(
                FlagConfigStreamer(flagConfigStreamApi, flagConfigStorage, cohortLoader, cohortStorage, config, metrics),
                amplitudeFlagConfigPoller,
            )
        else amplitudeFlagConfigPoller
    private val flagConfigUpdater =
        if (flagConfigProxyApi != null)
            FlagConfigFallbackRetryWrapper(
                FlagConfigPoller(flagConfigProxyApi, flagConfigStorage, cohortLoader, cohortStorage, config, metrics),
                amplitudeFlagConfigPoller
            )
        else
            amplitudeFlagConfigUpdater

    fun start() = lock.once {
        flagConfigUpdater.start()
        if (cohortLoader != null) {
            poller.scheduleWithFixedDelay(
                {
                    try {
                        val cohortIds = flagConfigStorage.getFlagConfigs().values.getAllCohortIds()
                        for (cohortId in cohortIds) {
                            cohortLoader.loadCohort(cohortId)
                        }
                    } catch (t: Throwable) {
                        Logger.e("Refresh cohorts failed.", t)
                    }
                }, cohortPollingInterval,
                cohortPollingInterval,
                TimeUnit.MILLISECONDS
            )
        }
    }

    fun stop() {
        poller.shutdown()
        flagConfigUpdater.shutdown()
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
