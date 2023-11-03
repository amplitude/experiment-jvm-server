@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.util

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics

open class LocalEvaluationMetricsCounter : LocalEvaluationMetrics {

    var evaluation = 0
    var evaluationFailure = 0
    var assignment = 0
    var assignmentFilter = 0
    var assignmentEvent = 0
    var assignmentEventFailure = 0
    var flagConfigFetch = 0
    var flagConfigFetchFailure = 0
    var flagConfigFetchOriginFallback = 0
    var cohortDescriptionsFetch = 0
    var cohortDescriptionsFetchFailure = 0
    var cohortDescriptionsFetchOriginFallback = 0
    var cohortDownload = 0
    var cohortDownloadFailure = 0
    var cohortDownloadOriginFallback = 0
    var cohortMembership = 0
    var cohortMembershipFailure = 0

    override fun onEvaluation() {
        evaluation++
    }

    override fun onEvaluationFailure(exception: Exception) {
        evaluationFailure++
    }

    override fun onAssignment() {
        assignment++
    }

    override fun onAssignmentFilter() {
        assignmentFilter++
    }

    override fun onAssignmentEvent() {
        assignmentEvent++
    }

    override fun onAssignmentEventFailure(exception: Exception) {
        Logger.e("onAssignmentEventFailure", exception)
        assignmentEventFailure++
    }

    override fun onFlagConfigFetch() {
        flagConfigFetch++
    }

    override fun onFlagConfigFetchFailure(exception: Exception) {
        Logger.e("onFlagConfigFetchFailure", exception)
        flagConfigFetchFailure++
    }

    override fun onFlagConfigFetchOriginFallback(exception: Exception) {
        Logger.e("onFlagConfigFetchOriginFallback", exception)
        flagConfigFetchOriginFallback++
    }

    override fun onCohortDescriptionsFetch() {
        cohortDescriptionsFetch++
    }

    override fun onCohortDescriptionsFetchFailure(exception: Exception) {
        Logger.e("onCohortDescriptionsFetchFailure", exception)
        cohortDescriptionsFetchFailure++
    }

    override fun onCohortDescriptionsFetchOriginFallback(exception: Exception) {
        Logger.e("onCohortDescriptionsFetchOriginFallback", exception)
        cohortDescriptionsFetchOriginFallback++
    }

    override fun onCohortDownload() {
        cohortDownload++
    }

    override fun onCohortDownloadFailure(exception: Exception) {
        Logger.e("onCohortDownloadFailure", exception)
        cohortDownloadFailure++
    }

    override fun onCohortDownloadOriginFallback(exception: Exception) {
        Logger.e("onCohortDownloadOriginFallback", exception)
        cohortDownloadOriginFallback++
    }

    override fun onCohortMembership() {
        cohortMembership++
    }

    override fun onCohortMembershipFailure(exception: Exception) {
        Logger.e("onCohortMembershipFailure", exception)
        cohortMembershipFailure++
    }

    override fun toString(): String {
        return "LocalEvaluationMetricsCounter(evaluation=$evaluation, evaluationFailure=$evaluationFailure, assignment=$assignment, assignmentFilter=$assignmentFilter, assignmentEvent=$assignmentEvent, assignmentEventFailure=$assignmentEventFailure, flagConfigFetch=$flagConfigFetch, flagConfigFetchFailure=$flagConfigFetchFailure, flagConfigFetchOriginFallback=$flagConfigFetchOriginFallback, cohortDescriptionsFetch=$cohortDescriptionsFetch, cohortDescriptionsFetchFailure=$cohortDescriptionsFetchFailure, cohortDescriptionsFetchOriginFallback=$cohortDescriptionsFetchOriginFallback, cohortDownload=$cohortDownload, cohortDownloadFailure=$cohortDownloadFailure, cohortDownloadOriginFallback=$cohortDownloadOriginFallback, cohortMembership=$cohortMembership, cohortMembershipFailure=$cohortMembershipFailure)"
    }
}
