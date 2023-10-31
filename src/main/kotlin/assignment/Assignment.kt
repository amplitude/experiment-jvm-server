package com.amplitude.experiment.assignment

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.evaluation.EvaluationVariant

internal const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000

internal data class Assignment(
    val user: ExperimentUser,
    val results: Map<String, EvaluationVariant>,
    val timestamp: Long = System.currentTimeMillis(),
)

internal fun Assignment.canonicalize(): String {
    val sb = StringBuilder().append(this.user.userId?.trim(), " ", this.user.deviceId?.trim(), " ")
    for (key in this.results.keys.sorted()) {
        val value = this.results[key]
        sb.append(key.trim(), " ", value?.key?.trim(), " ")
    }
    return sb.toString()
}
