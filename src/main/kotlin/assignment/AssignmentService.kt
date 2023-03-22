@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.assignment

import com.amplitude.Amplitude
import com.amplitude.AmplitudeCallbacks
import com.amplitude.Event
import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.evaluation.FLAG_TYPE_MUTUAL_EXCLUSION_GROUP
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.wrapMetrics
import org.json.JSONObject

internal interface AssignmentService {
    fun track(assignment: Assignment)
}

class EventTrackingException(
    event: Event,
    status: Int,
    message: String?
) : Exception(
    "Failed to track event to amplitude: event=${event.eventType}, status=$status, msg=$message"
)

internal class AmplitudeAssignmentService(
    private val amplitude: Amplitude,
    private val assignmentFilter: AssignmentFilter,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) : AssignmentService {

    init {
        amplitude.setCallbacks(object : AmplitudeCallbacks() {
            override fun onLogEventServerResponse(event: Event?, status: Int, message: String?) {
                if (event == null) return
                if (status != 200) {
                    metrics.onAssignmentEventFailure(EventTrackingException(event, status, message))
                }
            }
        })
    }

    override fun track(assignment: Assignment) {
        metrics.onAssignment()
        if (assignmentFilter.shouldTrack(assignment)) {
            wrapMetrics(
                metric = metrics::onAssignmentEvent,
                failure = metrics::onAssignmentEventFailure,
            ) {
                amplitude.logEvent(assignment.toAmplitudeEvent())
            }
        } else {
            metrics.onAssignmentFilter()
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
            if (result.type == FLAG_TYPE_MUTUAL_EXCLUSION_GROUP) {
                // Dont set user properties for mutual exclusion groups.
                continue
            } else if (result.isDefaultVariant) {
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
