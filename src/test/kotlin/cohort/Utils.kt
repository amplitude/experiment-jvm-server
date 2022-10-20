package com.amplitude.experiment.cohort

// For Testing
internal fun cohortDescription(
    id: String,
    lastComputed: Long = 0,
    size: Int = 0
): CohortDescription = CohortDescription(
    lastComputed = lastComputed,
    published = true,
    archived = false,
    appId = "",
    lastMod = 0,
    type = "",
    id = id,
    size = size,
)
