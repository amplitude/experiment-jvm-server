package com.amplitude.experiment.assignment

import com.amplitude.Amplitude
import com.amplitude.AmplitudeCallbacks
import com.amplitude.Event
import org.json.JSONObject

private object FlagType {
    const val RELEASE = "release"
    const val EXPERIMENT = "experiment"
    const val MUTUAL_EXCLUSION_GROUP = "mutual-exclusion-group"
    const val HOLDOUT_GROUP = "holdout-group"
    const val RELEASE_GROUP = "release-group"
}

internal interface AssignmentService {
    fun track(assignment: Assignment)
}

internal class AmplitudeAssignmentService(
    private val amplitude: Amplitude,
    private val assignmentFilter: AssignmentFilter,
) : AssignmentService {

    override fun track(assignment: Assignment) {
        if (assignmentFilter.shouldTrack(assignment)) {
            amplitude.logEvent(assignment.toAmplitudeEvent())
        }
    }
}

internal fun Assignment.toAmplitudeEvent(): Event {
    val event = Event(
        "[Experiment] Assignment",
        this.user.userId,
        this.user.deviceId
    )
    if (!user.groups.isNullOrEmpty()) {
        event.groups = JSONObject(user.groups)
    }
    event.eventProperties = JSONObject().apply {
        for ((flagKey, variant) in this@toAmplitudeEvent.results) {
            val version = variant.metadata?.get("flagVersion")
            val segmentName = variant.metadata?.get("segmentName")
            val details = "v$version rule:$segmentName"
            put("$flagKey.variant", variant.key)
            put("$flagKey.details", details)
        }
    }
    event.userProperties = JSONObject().apply {
        val set = JSONObject()
        val unset = JSONObject()
        for ((flagKey, variant) in this@toAmplitudeEvent.results) {
            val flagType = variant.metadata?.get("flagType") as? String
            val default = variant.metadata?.get("default") as? Boolean ?: false
            if (flagType == FlagType.MUTUAL_EXCLUSION_GROUP) {
                // Dont set user properties for mutual exclusion groups.
                continue
            } else if (default) {
                unset.put("[Experiment] $flagKey", "-")
            } else {
                set.put("[Experiment] $flagKey", variant.key)
            }
        }
        put("\$set", set)
        put("\$unset", unset)
    }
    event.insertId = "${this.user.userId} ${this.user.deviceId} ${this.canonicalize().hashCode()} ${this.timestamp / DAY_MILLIS}"
    return event
}
