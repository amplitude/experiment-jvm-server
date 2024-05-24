@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.deployment

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.daemonFactory
import com.amplitude.experiment.util.getAllCohortIds
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DeploymentRunner(
    private val config: LocalEvaluationConfig,
    private val flagConfigApi: FlagConfigApi,
    private val flagConfigStorage: FlagConfigStorage,
    private val cohortStorage: CohortStorage,
    private val cohortLoader: CohortLoader?,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) {

    private val lock = Once()
    private val poller = Executors.newScheduledThreadPool(1, daemonFactory)

    fun start() = lock.once {
        refresh()
        poller.scheduleWithFixedDelay(
            {
                try {
                    refresh()
                } catch (t: Throwable) {
                    Logger.e("Refresh flag configs failed.", t)
                }
            },
            config.flagConfigPollerIntervalMillis,
            config.flagConfigPollerIntervalMillis,
            TimeUnit.MILLISECONDS
        )
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
                },
                60,
                60,
                TimeUnit.SECONDS
            )
        }
    }

    fun stop() {
        poller.shutdown()
    }

    private fun refresh() {
        Logger.d("Refreshing flag configs.")
        // Get updated flags from the network.
        val flagConfigs = wrapMetrics(
            metric = metrics::onFlagConfigFetch,
            failure = metrics::onFlagConfigFetchFailure,
        ) {
            flagConfigApi.getFlagConfigs()
        }
        // Remove flags that no longer exist.
        val flagKeys = flagConfigs.map { it.key }.toSet()
        flagConfigStorage.removeIf { !flagKeys.contains(it.key) }
        // Load cohorts for each flag if applicable and put the flag in storage.
        val futures = ConcurrentHashMap<String, CompletableFuture<*>>()
        for (flagConfig in flagConfigs) {
            val cohortIds = flagConfig.getAllCohortIds()
            if (cohortLoader == null || cohortIds.isEmpty()) {
                flagConfigStorage.putFlagConfig(flagConfig)
                continue
            }
            for (cohortId in cohortIds) {
                futures.putIfAbsent(
                    cohortId,
                    cohortLoader.loadCohort(cohortId).thenRun {
                        flagConfigStorage.putFlagConfig(flagConfig)
                    }
                )
            }
        }
        futures.values.forEach { it.join() }
        // Delete unused cohorts
        val flagCohortIds = flagConfigStorage.getFlagConfigs().values.toList().getAllCohortIds()
        val storageCohorts = cohortStorage.getCohortDescriptions()
        val deletedCohortIds = storageCohorts.keys - flagCohortIds
        for (deletedCohortId in deletedCohortIds) {
            val deletedCohortDescription = storageCohorts[deletedCohortId]
            if (deletedCohortDescription != null) {
                cohortStorage.deleteCohort(deletedCohortDescription.groupType, deletedCohortId)
            }
        }
        Logger.d("Refreshed ${flagConfigs.size} flag configs.")
    }
}
