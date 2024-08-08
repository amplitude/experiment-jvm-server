@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.util

import com.amplitude.experiment.ExperimentalApi
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

internal class LocalEvaluationMetricsWrapper(
    private val metrics: LocalEvaluationMetrics? = null
) : LocalEvaluationMetrics {

    private val executor = if (metrics != null) {
        Executors.newFixedThreadPool(1, daemonFactory)
    } else {
        null
    }

    override fun onEvaluation() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onEvaluation() }
    }

    override fun onEvaluationFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onEvaluationFailure(exception) }
    }

    override fun onAssignment() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onAssignment() }
    }

    override fun onAssignmentFilter() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onAssignmentFilter() }
    }

    override fun onAssignmentEvent() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onAssignmentEvent() }
    }

    override fun onAssignmentEventFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onAssignmentEventFailure(exception) }
    }

    override fun onFlagConfigFetch() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onFlagConfigFetch() }
    }

    override fun onFlagConfigFetchFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onFlagConfigFetchFailure(exception) }
    }

    override fun onFlagConfigFetchOriginFallback(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onFlagConfigFetchOriginFallback(exception) }
    }

    override fun onCohortDownload() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onCohortDownload() }
    }

    override fun onCohortDownloadFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onCohortDownloadFailure(exception) }
    }

    override fun onCohortDownloadOriginFallback(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onCohortDownloadOriginFallback(exception) }
    }

    override fun onProxyCohortMembership() {
        val metrics = metrics ?: return
        executor?.execute { metrics.onProxyCohortMembership() }
    }

    override fun onProxyCohortMembershipFailure(exception: Exception) {
        val metrics = metrics ?: return
        executor?.execute { metrics.onProxyCohortMembershipFailure(exception) }
    }
}
