package com.amplitude.experiment.util

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
    var cohortDescriptionsFetch = 0
    var cohortDescriptionsFetchFailure = 0
    var cohortDownload = 0
    var cohortDownloadFailure = 0

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
        assignmentEventFailure++
    }

    override fun onFlagConfigFetch() {
        flagConfigFetch++
    }

    override fun onFlagConfigFetchFailure(exception: Exception) {
        flagConfigFetchFailure++
    }

    override fun onCohortDescriptionsFetch() {
        cohortDescriptionsFetch++
    }

    override fun onCohortDescriptionsFetchFailure(exception: Exception) {
        cohortDescriptionsFetchFailure++
    }

    override fun onCohortDownload() {
        cohortDownload++
    }

    override fun onCohortDownloadFailure(exception: Exception) {
        cohortDownloadFailure++
    }
}
