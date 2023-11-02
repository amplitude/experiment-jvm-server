@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class CohortLoader(
    private val maxCohortSize: Int,
    private val cohortDownloadApi: CohortDownloadApi,
    private val cohortStorage: CohortStorage,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) {

    private val jobs = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val executor = ThreadPoolExecutor(
        1,
        32,
        60,
        TimeUnit.SECONDS,
        LinkedBlockingQueue()
    )

    fun loadCohort(cohortId: String): CompletableFuture<*> {
        return jobs.getOrPut(cohortId) {
            CompletableFuture.runAsync({
                Logger.d("Loading cohort $cohortId")
                val cohortDescription = getCohortDescription(cohortId)
                if (shouldDownloadCohort(cohortDescription)) {
                    val cohortMembers = downloadCohorts(cohortDescription)
                    cohortStorage.putCohort(cohortDescription, cohortMembers)
                }
            }, executor).thenRun { jobs.remove(cohortId) }
        }
    }

    private fun getCohortDescription(cohortIds: String): CohortDescription {
        return wrapMetrics(
            metric = metrics::onCohortDescriptionsFetch,
            failure = metrics::onCohortDescriptionsFetchFailure,
        ) {
            cohortDownloadApi.getCohortDescription(cohortIds)
        }
    }

    private fun shouldDownloadCohort(cohortDescription: CohortDescription): Boolean {
        val storageDescription = cohortStorage.getCohortDescription(cohortDescription.id)
        return cohortDescription.size <= maxCohortSize &&
            cohortDescription.lastComputed > (storageDescription?.lastComputed ?: -1)
    }

    private fun downloadCohorts(cohortDescription: CohortDescription): Set<String> {
        return wrapMetrics(
            metric = metrics::onCohortDownload,
            failure = metrics::onCohortDownloadFailure,
        ) {
            cohortDownloadApi.getCohortMembers(cohortDescription)
        }
    }
}
