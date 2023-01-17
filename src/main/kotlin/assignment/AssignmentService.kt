package com.amplitude.experiment.assignment

import com.amplitude.Amplitude
import com.amplitude.Event
import org.json.JSONObject



internal const val DEFAULT_EVENT_UPLOAD_THRESHOLD = 10
internal const val DEFAULT_EVENT_UPLOAD_PERIOD_MILLIS = 10000

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
    event.eventProperties = JSONObject().apply {
        for ((flagKey, result) in this@toAmplitudeEvent.results) {
            put("$flagKey.variant", result.variant.key)
            put("$flagKey.details", result.description)
        }
    }
    event.userProperties = JSONObject().apply {
        val set = JSONObject()
        val unset = JSONObject()
        for ((flagKey, result) in this@toAmplitudeEvent.results) {
            if (result.isDefaultVariant) {
                unset.put("[Experiment] $flagKey", "-")
            } else {
                set.put("[Experiment] $flagKey", result.variant.key)
            }
        }
        put("\$set", set)
        put("\$unset", unset)
    }
    event.insertId = "${this.user.userId} ${this.user.deviceId} ${this.canonicalize().hashCode()} ${this.timestamp / DAY_MILLIS}"
    return event
}
