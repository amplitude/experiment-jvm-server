package com.amplitude.experiment.cohort

internal data class CohortDescription(
    val id: String,
    val lastComputed: Long,
    val size: Int,
)
