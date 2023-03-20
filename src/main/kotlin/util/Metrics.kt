package com.amplitude.experiment.util

import com.amplitude.experiment.LocalEvaluationMetrics
import java.util.concurrent.Executors

internal fun <R> wrapMetrics(metric: (() -> Unit)?, failure: ((e: Exception) -> Unit)?, block: () -> R): R {
    try {
        metric?.invoke()
        return block.invoke()
    } catch (e: Exception) {
        failure?.invoke(e)
        throw e
    }
}

internal class LocalEvaluationMetricsWrapper : LocalEvaluationMetrics {

    var metrics: LocalEvaluationMetrics? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onEvaluation() {
        val metrics = metrics ?: return
        executor.execute { metrics.onEvaluation() }
    }

    override fun onEvaluationFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor.execute { metrics.onEvaluationFailure(exception) }
    }

    override fun onAssignment() {
        val metrics = metrics ?: return
        executor.execute { metrics.onAssignment() }
    }

    override fun onAssignmentFilter() {
        val metrics = metrics ?: return
        executor.execute { metrics.onAssignmentFilter() }
    }

    override fun onAssignmentEvent() {
        val metrics = metrics ?: return
        executor.execute { metrics.onAssignmentEvent() }
    }

    override fun onAssignmentEventFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor.execute { metrics.onAssignmentEventFailure(exception) }
    }

    override fun onFlagConfigFetch() {
        val metrics = metrics ?: return
        executor.execute { metrics.onFlagConfigFetch() }
    }

    override fun onFlagConfigFetchFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor.execute { metrics.onFlagConfigFetchFailure(exception) }
    }

    override fun onCohortDescriptionsFetch() {
        val metrics = metrics ?: return
        executor.execute { metrics.onCohortDescriptionsFetch() }
    }

    override fun onCohortDescriptionsFetchFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor.execute { metrics.onCohortDescriptionsFetchFailure(exception) }
    }

    override fun onCohortDownload() {
        val metrics = metrics ?: return
        executor.execute { metrics.onCohortDownload() }
    }

    override fun onCohortDownloadFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor.execute { metrics.onCohortDownloadFailure(exception) }
    }
}
