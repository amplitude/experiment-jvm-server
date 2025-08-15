package com.amplitude.experiment.assignment

import com.amplitude.Amplitude
import com.amplitude.AmplitudeCallbacks
import com.amplitude.Event
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.wrapMetrics
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
                try {
                    if (event == null) return
                    if (status != 200) {
                        metrics.onAssignmentEventFailure(EventTrackingException(event, status, message))
                    }
                } catch (e: Exception) {
                    Logger.e("Failed log event server response.", e)
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
