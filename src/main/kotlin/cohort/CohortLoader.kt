@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.daemonFactory
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
    // TODO: Remove once dedicated API is implemented.
    private val directCohortDownloadApi: DirectCohortDownloadApiV5? = null,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) {

    private val jobs = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val cachedJobs = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val executor = ThreadPoolExecutor(
        32,
        32,
        60,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        daemonFactory,
    ).apply {
        allowCoreThreadTimeOut(true)
    }

    fun loadCohort(cohortId: String): CompletableFuture<*> {
        return jobs.getOrPut(cohortId) {
            CompletableFuture.runAsync({
                Logger.d("Loading cohort $cohortId")
                val cohortDescription = getCohortDescription(cohortId)
                if (shouldDownloadCohort(cohortDescription)) {
                    val cohortMembers = downloadCohort(cohortDescription)
                    cohortStorage.putCohort(cohortDescription, cohortMembers)
                }
            }, executor).whenComplete { _, _ -> jobs.remove(cohortId) }
        }
    }

    fun loadCachedCohort(cohortId: String): CompletableFuture<*> {
        return cachedJobs.getOrPut(cohortId) {
            CompletableFuture.runAsync({
                Logger.d("Loading cohort from cache $cohortId")
                // Cached cohorts should be refreshed, so set last computed to 0.
                val cohortDescription = getCohortDescription(cohortId).copy(lastComputed = 0)
                if (shouldDownloadCohort(cohortDescription)) {
                    val cohortMembers = downloadCachedCohort(cohortDescription)
                    cohortStorage.putCohort(cohortDescription, cohortMembers)
                }
            }, executor).whenComplete { _, _ -> cachedJobs.remove(cohortId) }
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

    private fun downloadCohort(cohortDescription: CohortDescription): Set<String> {
        return wrapMetrics(
            metric = metrics::onCohortDownload,
            failure = metrics::onCohortDownloadFailure,
        ) {
            try {
                cohortDownloadApi.getCohortMembers(cohortDescription)
            } catch (e: CachedCohortDownloadException) {
                metrics.onCohortDownloadFailureCachedFallback(e.cause)
                e.members
            }
        }
    }

    private fun downloadCachedCohort(cohortDescription: CohortDescription): Set<String> {
        return wrapMetrics(
            metric = metrics::onCohortDownload,
            failure = metrics::onCohortDownloadFailure,
        ) {
            directCohortDownloadApi?.getCachedCohortMembers(cohortDescription.id, cohortDescription.groupType)
                ?: cohortDownloadApi.getCohortMembers(cohortDescription)
        }
    }
}
