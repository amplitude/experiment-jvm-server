package com.amplitude.experiment.util

import com.amplitude.experiment.evaluation.EvaluationCondition
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.EvaluationOperator
import com.amplitude.experiment.evaluation.EvaluationSegment

internal fun Collection<EvaluationFlag>.getCohortIds(): Set<String> {
    val cohortIds = mutableSetOf<String>()
    for (flag in this) {
        cohortIds += flag.getCohortIds()
    }
    return cohortIds
}

internal fun EvaluationFlag.getCohortIds(): Set<String> {
    val cohortIds = mutableSetOf<String>()
    for (segment in this.segments) {
        cohortIds += segment.getCohortConditionIds()
    }
    return cohortIds
}

private fun EvaluationSegment.getCohortConditionIds(): Set<String> {
    val cohortIds = mutableSetOf<String>()
    if (conditions == null) {
        return cohortIds
    }
    for (outer in conditions!!) {
        for (condition in outer) {
            if (condition.isCohortFilter()) {
                cohortIds += condition.values
            }
        }
    }
    return cohortIds
}

// Only cohort filters use these operators.
private fun EvaluationCondition.isCohortFilter(): Boolean =
    this.op == EvaluationOperator.SET_CONTAINS_ANY || this.op == EvaluationOperator.SET_DOES_NOT_CONTAIN_ANY
