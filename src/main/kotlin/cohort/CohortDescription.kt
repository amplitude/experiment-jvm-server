package com.amplitude.experiment.cohort

import kotlinx.serialization.Serializable

@Serializable
internal data class CohortDescription(
    val id: String,
    val lastComputed: Long,
    val size: Int,
)
