@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.util

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import java.util.concurrent.Executor
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
        tryExecute(executor) { metrics.onEvaluation() }
    }

    override fun onEvaluationFailure(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onEvaluationFailure(exception) }
    }

    override fun onAssignment() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onAssignment() }
    }

    override fun onAssignmentFilter() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onAssignmentFilter() }
    }

    override fun onAssignmentEvent() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onAssignmentEvent() }
    }

    override fun onAssignmentEventFailure(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onAssignmentEventFailure(exception) }
    }

    override fun onFlagConfigFetch() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onFlagConfigFetch() }
    }

    override fun onFlagConfigFetchFailure(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onFlagConfigFetchFailure(exception) }
    }

    override fun onFlagConfigStream() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onFlagConfigStream() }
    }

    override fun onFlagConfigStreamFailure(exception: Exception?) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onFlagConfigStreamFailure(exception) }
    }

    override fun onFlagConfigFetchOriginFallback(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onFlagConfigFetchOriginFallback(exception) }
    }

    override fun onCohortDownload() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onCohortDownload() }
    }

    override fun onCohortDownloadTooLarge(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onCohortDownloadTooLarge(exception) }
    }

    override fun onCohortDownloadFailure(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onCohortDownloadFailure(exception) }
    }

    override fun onCohortDownloadOriginFallback(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onCohortDownloadOriginFallback(exception) }
    }

    override fun onProxyCohortMembership() {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onProxyCohortMembership() }
    }

    override fun onProxyCohortMembershipFailure(exception: Exception) {
        val metrics = metrics ?: return
        tryExecute(executor) { metrics.onProxyCohortMembershipFailure(exception) }
    }

    private fun tryExecute(executor: Executor?, block: () -> Unit) {
        executor?.execute {
            try {
                block.invoke()
            } catch (e: Exception) {
                Logger.error("Failed to execute metrics.", e)
            }
        }
    }
}
