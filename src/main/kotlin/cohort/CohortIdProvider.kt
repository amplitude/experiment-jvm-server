package com.amplitude.experiment.cohort

import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.UserPropertyFilter

internal const val COHORT_PROP_KEY = "userdata_cohort"

typealias CohortIdProvider = () -> Set<String>

internal fun Collection<FlagConfig>.getCohortIds(): Set<String> {
    val cohortIds = mutableSetOf<String>()
    for (flag in this) {
        for (filter in flag.allUsersTargetingConfig.conditions) {
            if (filter.isCohortFilter()) {
                cohortIds += filter.values
            }
        }
        val customSegments = flag.customSegmentTargetingConfigs ?: listOf()
        for (segment in customSegments) {
            for (filter in segment.conditions) {
                if (filter.isCohortFilter()) {
                    cohortIds += filter.values
                }
            }
        }
    }
    return cohortIds
}

private fun UserPropertyFilter.isCohortFilter(): Boolean = this.prop == COHORT_PROP_KEY
