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

internal class CohortSyncService(
    private val config: CohortSyncConfiguration,
    private val cohortDownloadApi: CohortDownloadApi,
    private val cohortStorage: CohortStorage,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) {

    private val start = Once()
    private val refreshLock = Any()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    internal val managedCohorts = mutableSetOf<String>()

    fun start() = start.once {
        scheduledExecutor.scheduleWithFixedDelay(
            {
                try {
                    synchronized(refreshLock) {
                        refresh(managedCohorts)
                    }
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
        Logger.d("Refreshing cohorts $cohortIds")
        val refreshCohortIds = if (cohortIds != null) {
            val deletedCohortsIds = managedCohorts - cohortIds
            val addedCohortIds = cohortIds - managedCohorts
            managedCohorts.clear()
            managedCohorts.addAll(cohortIds)
            for (cohortId in deletedCohortsIds) {
                cohortStorage.deleteCohort(cohortId)
            }
            addedCohortIds
        } else {
            managedCohorts
        }
        val networkCohortDescriptions = getCohortDescriptions()
        val filteredCohorts = filterCohorts(networkCohortDescriptions, refreshCohortIds)
        downloadCohorts(filteredCohorts)
    }

    internal fun getCohortDescriptions(): List<CohortDescription> {
        return wrapMetrics(
            metric = metrics::onCohortDescriptionsFetch,
            failure = metrics::onCohortDescriptionsFetchFailure,
        ) {
            cohortDownloadApi.getCohortDescriptions(managedCohorts)
        }
    }

    /**
     * Filter cohorts received from network. Removes cohorts which are:
     *   1. Not requested for management by this function.
     *   2. Larger than the max size.
     *   3. Are equal to what has been downloaded already.
     */
    internal fun filterCohorts(
        networkCohortDescriptions: List<CohortDescription>,
        cohortIds: Set<String>
    ): List<CohortDescription> {
        return networkCohortDescriptions.filter { networkCohortDescription ->
            val storageDescription = cohortStorage.getCohortDescription(networkCohortDescription.id)
            cohortIds.contains(networkCohortDescription.id) &&
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
