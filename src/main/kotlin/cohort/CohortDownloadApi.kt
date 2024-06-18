package com.amplitude.experiment.cohort

interface CohortDownloadApi {
    fun getCohort(id: String, maxCohortSize: Int): Cohort
}
