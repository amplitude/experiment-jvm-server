package com.amplitude.experiment.cohort

import kotlinx.serialization.Serializable

const val USER_GROUP_TYPE = "User"

@Serializable
internal data class CohortDescription(
    val id: String,
    val lastComputed: Long,
    val size: Int,
    val groupType: String = USER_GROUP_TYPE
)
