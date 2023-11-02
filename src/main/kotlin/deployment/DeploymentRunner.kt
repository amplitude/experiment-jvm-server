@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.deployment

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.getCohortIds
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.CompletableFuture
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
    private val poller = Executors.newSingleThreadScheduledExecutor()

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
                    val cohortIds = flagConfigStorage.getFlagConfigs().values.getCohortIds()
                    for (cohortId in cohortIds) {
                        cohortLoader.loadCohort(cohortId)
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
        val futures = mutableListOf<CompletableFuture<*>>()
        for (flagConfig in flagConfigs) {
            val cohortIds = flagConfig.getCohortIds()
            if (cohortLoader != null && cohortIds.isNotEmpty()) {
                for (cohortId in cohortIds) {
                    futures += cohortLoader.loadCohort(cohortId).thenRun {
                        flagConfigStorage.putFlagConfig(flagConfig)
                    }
                }
            } else {
                flagConfigStorage.putFlagConfig(flagConfig)
            }
        }
        futures.forEach { it.join() }
        // Delete unused cohorts
        val flagCohortIds = flagConfigStorage.getFlagConfigs().values.toList().getCohortIds()
        val storageCohortIds = cohortStorage.getCohortDescriptions().keys
        val deletedCohortIds = storageCohortIds - flagCohortIds
        for (deletedCohortId in deletedCohortIds) {
            cohortStorage.deleteCohort(deletedCohortId)
        }
        Logger.d("Refreshed ${flagConfigs.size} flag configs.")
    }
}
