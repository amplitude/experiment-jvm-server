package com.amplitude.experiment.cohort

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
    private val cohortApi: CohortApi,
    private val cohortStorage: CohortStorage,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) {

    private val jobs = ConcurrentHashMap<String, CompletableFuture<*>>()
    private val executor = ThreadPoolExecutor(
        32,
        32,
        60L,
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
                val storageCohort = cohortStorage.getCohort(cohortId)
                wrapMetrics(
                    metrics::onCohortDownload,
                    metrics::onCohortDownloadFailure
                ) {
                    try {
                        val cohort = cohortApi.getCohort(cohortId, storageCohort)
                        cohortStorage.putCohort(cohort)
                    } catch (e: CohortNotModifiedException) {
                        // Do nothing
                    } catch (e: CohortTooLargeException) {
                        Logger.e("Cohort too large", e)
                    }
                }
            }, executor).whenComplete { _, _ -> jobs.remove(cohortId) }
        }
    }
}
