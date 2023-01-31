package com.amplitude.experiment.assignment

import com.amplitude.Amplitude
import com.amplitude.Event
import com.amplitude.experiment.util.Logger
import org.json.JSONObject

internal const val DEFAULT_EVENT_UPLOAD_THRESHOLD = 10
internal const val DEFAULT_EVENT_UPLOAD_PERIOD_MILLIS = 10000
internal const val MAX_ASSIGNMENTS_PER_EVENT = 1024

internal interface AssignmentService {
    fun track(assignment: Assignment)
}

internal class AmplitudeAssignmentService(
    private val amplitude: Amplitude,
    private val assignmentFilter: AssignmentFilter,
) : AssignmentService {

    override fun track(assignment: Assignment) {
        if (assignment.user.userId.isNullOrEmpty() && assignment.user.deviceId.isNullOrEmpty()) {
            Logger.w("Cannot track assignment for user without user ID or device ID")
            return
        }
        if (assignmentFilter.shouldTrack(assignment)) {
            Logger.d("Tracking assignment event: $assignment")
            if (assignment.results.size > MAX_ASSIGNMENTS_PER_EVENT) {
                assignment.chunkAssignmentToEvents().forEach {
                    amplitude.logEvent(it)
                }
            } else {
                amplitude.logEvent(assignment.toAmplitudeEvent())
            }
        } else {
            Logger.d("Filtered out assignment event: $assignment")
        }
    }
}

internal fun Assignment.chunkAssignmentToEvents(): List<Event> {
    return results
        .toList()
        .chunked(1000)
        .map {
            Assignment(user, it.toMap(), timestamp).toAmplitudeEvent()
        }
}

internal fun Assignment.toAmplitudeEvent(): Event {
    val event = Event(
        "[Experiment] Assignment",
        this.user.userId,
        this.user.deviceId
    )
    event.eventProperties = JSONObject()
    event.userProperties = JSONObject()
    val set = JSONObject()
    val unset = JSONObject()
    for ((flagKey, result) in this.results) {
        event.eventProperties.put("$flagKey.variant", result.variant.key)
        if (result.isDefaultVariant) {
            unset.put("[Experiment] $flagKey", "-")
        } else {
            set.put("[Experiment] $flagKey", result.variant.key)
        }
    }
    if (!set.isEmpty) {
        event.userProperties.put("\$set", set)
    }
    if (!unset.isEmpty) {
        event.userProperties.put("\$unset", unset)
    }
    event.eventProperties = JSONObject().apply {
        for ((flagKey, result) in this@toAmplitudeEvent.results) {
            put("$flagKey.variant", result.variant.key)
        }
    }
    event.insertId = "${this.user.userId} ${this.user.deviceId} ${this.canonicalize().hashCode()} ${this.timestamp / DAY_MILLIS}"
    return event
}
