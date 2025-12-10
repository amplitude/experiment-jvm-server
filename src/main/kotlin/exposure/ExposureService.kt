package com.amplitude.experiment.exposure

import com.amplitude.Amplitude
import com.amplitude.AmplitudeCallbacks
import com.amplitude.Event
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.wrapMetrics
import org.json.JSONObject

internal object FlagType {
    const val RELEASE = "release"
    const val EXPERIMENT = "experiment"
    const val MUTUAL_EXCLUSION_GROUP = "mutual-exclusion-group"
    const val HOLDOUT_GROUP = "holdout-group"
    const val RELEASE_GROUP = "release-group"
}

internal interface ExposureService {
    fun track(exposure: Exposure)
}

internal class EventTrackingException(
    event: Event,
    status: Int,
    message: String?
) : Exception(
    "Failed to track event to amplitude: event=${event.eventType}, status=$status, msg=$message"
)

internal class AmplitudeExposureService constructor(
    private val amplitude: Amplitude,
    private val exposureFilter: ExposureFilter,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) : ExposureService {

    init {
        amplitude.setCallbacks(object : AmplitudeCallbacks() {
            override fun onLogEventServerResponse(event: Event?, status: Int, message: String?) {
                try {
                    if (event == null) return
                    if (status != 200) {
                        metrics.onExposureEventFailure(EventTrackingException(event, status, message))
                    }
                } catch (e: Exception) {
                    Logger.error("Failed log event server response.", e)
                }
            }
        })
    }

    override fun track(exposure: Exposure) {
        metrics.onExposure()
        if (exposureFilter.shouldTrack(exposure)) {
            wrapMetrics(
                metric = metrics::onExposureEvent,
                failure = metrics::onExposureEventFailure,
            ) {
                for (event in exposure.toAmplitudeEvent()) {
                    amplitude.logEvent(event)
                }
            }
        } else {
            metrics.onExposureFilter()
        }
    }
}

internal fun Exposure.toAmplitudeEvent(): List<Event> {
    val events = mutableListOf<Event>()
    val canonicalizedExposure = this.canonicalize()
    for ((flagKey, variant) in this@toAmplitudeEvent.results) {
        if (!(variant.metadata?.get("trackExposure") as? Boolean ?: true)) {
            continue
        }

        // Skip default variant exposures
        val isDefault = variant.metadata?.get("default") as? Boolean ?: false
        if (isDefault) {
            continue
        }

        val event = Event(
            "[Experiment] Exposure",
            this.user.userId,
            this.user.deviceId
        )
        if (!user.groups.isNullOrEmpty()) {
            event.groups = JSONObject(user.groups)
        }
        event.eventProperties = JSONObject().apply {
            put("[Experiment] Flag Key", flagKey)
            if (variant.key != null) {
                put("[Experiment] Variant", variant.key)
            } else if (variant.value != null) {
                put("[Experiment] Variant", variant.value)
            }
            if (variant.metadata != null) {
                put("metadata", JSONObject(variant.metadata.toMap()))
            }
        }
        event.userProperties = JSONObject().apply {
            val set = JSONObject()
            val unset = JSONObject()
            val flagType = variant.metadata?.get("flagType") as? String
            if (flagType != FlagType.MUTUAL_EXCLUSION_GROUP) {
                if (variant.key != null) {
                    set.put("[Experiment] $flagKey", variant.key)
                } else if (variant.value != null) {
                    set.put("[Experiment] $flagKey", variant.value)
                }
            }
            put("\$set", set)
            put("\$unset", unset)
        }
        event.insertId =
            "${this.user.userId} ${this.user.deviceId} ${"$flagKey $canonicalizedExposure".hashCode()} ${this.timestamp / DAY_MILLIS}"

        events.add(event)
    }
    return events
}
