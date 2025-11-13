package com.amplitude.experiment.exposure

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.Variant

internal const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000

internal data class Exposure(
    val user: ExperimentUser,
    val results: Map<String, Variant>,
    val timestamp: Long = System.currentTimeMillis(),
)

internal fun Exposure.canonicalize(): String {
    val sb = StringBuilder().append(this.user.userId?.trim(), " ", this.user.deviceId?.trim(), " ")
    for (key in this.results.keys.sorted()) {
        val value = this.results[key]
        sb.append(key.trim(), " ", value?.key?.trim(), " ")
    }
    return sb.toString()
}
