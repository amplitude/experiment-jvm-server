package com.amplitude.experiment.util

import com.amplitude.experiment.cohort.USER_GROUP_TYPE
import com.amplitude.experiment.evaluation.EvaluationCondition
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.EvaluationOperator
import com.amplitude.experiment.evaluation.EvaluationSegment

internal fun Collection<EvaluationFlag>.getAllCohortIds(): Set<String> {
    return getGroupedCohortIds().flatMap { it.value }.toSet()
}

internal fun Collection<EvaluationFlag>.getGroupedCohortIds(): Map<String, Set<String>> {
    val cohortIds = mutableMapOf<String, MutableSet<String>>()
    for (flag in this) {
        for (entry in flag.getGroupedCohortIds()) {
            cohortIds.getOrPut(entry.key) { mutableSetOf() } += entry.value
        }
    }
    return cohortIds
}

internal fun EvaluationFlag.getAllCohortIds(): Set<String> {
    return getGroupedCohortIds().flatMap { it.value }.toSet()
}

internal fun EvaluationFlag.getGroupedCohortIds(): Map<String, MutableSet<String>> {
    val cohortIds = mutableMapOf<String, MutableSet<String>>()
    for (segment in this.segments) {
        for (entry in segment.getGroupedCohortConditionIds()) {
            cohortIds.getOrPut(entry.key) { mutableSetOf() } += entry.value
        }
    }
    return cohortIds
}

private fun EvaluationSegment.getGroupedCohortConditionIds(): Map<String, MutableSet<String>> {
    val cohortIds = mutableMapOf<String, MutableSet<String>>()
    if (conditions == null) {
        return cohortIds
    }
    for (outer in conditions!!) {
        for (condition in outer) {
            if (condition.isCohortFilter()) {
                // User cohort selector is [context, user, cohort_ids]
                // Groups cohort selector is [context, groups, {group_type}, cohort_ids]
                if (condition.selector.size > 2) {
                    val contextSubtype = condition.selector[1]
                    val groupType = if (contextSubtype == "user") {
                        USER_GROUP_TYPE
                    } else if (condition.selector.contains("groups")) {
                        condition.selector[2]
                    } else {
                        continue
                    }
                    cohortIds.getOrPut(groupType) { mutableSetOf() } += condition.values
                }
            }
        }
    }
    return cohortIds
}

// Only cohort filters use these operators.
private fun EvaluationCondition.isCohortFilter(): Boolean =
    (this.op == EvaluationOperator.SET_CONTAINS_ANY || this.op == EvaluationOperator.SET_DOES_NOT_CONTAIN_ANY) &&
        this.selector.isNotEmpty() && this.selector.last() == "cohort_ids"
