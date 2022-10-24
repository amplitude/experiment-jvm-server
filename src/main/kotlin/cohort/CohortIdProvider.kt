package com.amplitude.experiment.cohort

import com.amplitude.experiment.evaluation.FlagConfig

typealias CohortIdProvider = () -> Set<String>

internal fun Collection<FlagConfig>.getCohortIds(): Set<String> {
    val cohortIds = mutableSetOf<String>()
    for (flag in this) {
        for (filter in flag.allUsersTargetingConfig.conditions) {
            if (filter.prop == "userdata_cohort") {
                cohortIds += filter.values
            }
        }
        val customSegments = flag.customSegmentTargetingConfigs ?: listOf()
        for (segment in customSegments) {
            for (filter in segment.conditions) {
                if (filter.prop == "userdata_cohort") {
                    cohortIds += filter.values
                }
            }
        }
    }
    return cohortIds
}
