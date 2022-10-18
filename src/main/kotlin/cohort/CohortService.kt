package com.amplitude.experiment.cohort

internal interface CohortService {
    fun start()
    fun stop()
    fun getCohorts(id: String)
}
