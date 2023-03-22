@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.CohortSyncConfiguration
import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val POLLING_INTERVAL_MILLIS = 60000L

internal class PollingCohortSyncService(
    private val config: CohortSyncConfiguration,
    private val cohortDownloadApi: CohortDownloadApi,
    private val cohortStorage: CohortStorage,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) {

    private val start = Once()
    private val refreshLock = Any()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val managedCohorts = mutableSetOf<String>()

    fun start() = start.once {
        scheduledExecutor.scheduleWithFixedDelay(
            {
                try {
                    refresh()
                } catch (t: Throwable) {
                    Logger.e("Cohort refresh failed.", t)
                }
            },
            POLLING_INTERVAL_MILLIS,
            POLLING_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        )
    }

    fun stop() {
        scheduledExecutor.shutdown()
    }

    fun refresh(cohortIds: Set<String>? = null) = synchronized(refreshLock) {
        if (cohortIds != null) {
            managedCohorts.clear()
            managedCohorts.addAll(cohortIds)
        }
        val networkCohortDescriptions = getCohortDescriptions()
        val filteredCohorts = filterCohorts(networkCohortDescriptions)
        downloadCohorts(filteredCohorts)
    }

    internal fun getCohortDescriptions(): List<CohortDescription> {
        return wrapMetrics(
            metric = metrics::onCohortDescriptionsFetch,
            failure = metrics::onCohortDescriptionsFetchFailure,
        ) {
            cohortDownloadApi.getCohortDescriptions()
        }
    }

    /**
     * Filter cohorts received from network. Removes cohorts which are:
     *   1. Not requested for management by this function.
     *   2. Larger than the max size.
     *   3. Are equal to what has been downloaded already.
     */
    internal fun filterCohorts(networkCohortDescriptions: List<CohortDescription>): List<CohortDescription> {
        return networkCohortDescriptions.filter { networkCohortDescription ->
            val storageDescription = cohortStorage.getCohortDescription(networkCohortDescription.id)
            managedCohorts.contains(networkCohortDescription.id) &&
                networkCohortDescription.size <= config.maxCohortSize &&
                networkCohortDescription.lastComputed > (storageDescription?.lastComputed ?: -1)
        }
    }

    internal fun downloadCohorts(cohortDescriptions: List<CohortDescription>) {
        for (cohortDescription in cohortDescriptions) {
            val cohortMembers = wrapMetrics(
                metric = metrics::onCohortDownload,
                failure = metrics::onCohortDownloadFailure,
            ) {
                cohortDownloadApi.getCohortMembers(cohortDescription)
            }
            cohortStorage.putCohort(cohortDescription, cohortMembers)
        }
    }
}
