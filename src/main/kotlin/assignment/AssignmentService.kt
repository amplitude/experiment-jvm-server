package com.amplitude.experiment.assignment

import com.amplitude.Amplitude
import com.amplitude.AmplitudeCallbacks
import com.amplitude.Event
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.exposure.EventTrackingException
import com.amplitude.experiment.exposure.FlagType
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.wrapMetrics
import org.json.JSONObject

@Deprecated(
    message = "Assignment service is deprecated. Use ExposureService with Exposure service instead.",
    replaceWith = ReplaceWith("com.amplitude.experiment.exposure.ExposureService"),
)
internal interface AssignmentService {
    fun track(assignment: Assignment)
}

@Deprecated(
    message = "Assignment service is deprecated. Use AmplitudeExposureService with Exposure service instead.",
    replaceWith = ReplaceWith("com.amplitude.experiment.exposure.AmplitudeExposureService"),
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
                    Logger.error("Failed log event server response.", e)
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
