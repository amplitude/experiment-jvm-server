package com.amplitude.experiment.assignment

import com.amplitude.experiment.util.Cache

@Deprecated(
    message = "Assignment service is deprecated. Use ExposureFilter with Exposure service instead.",
    replaceWith = ReplaceWith("ExposureFilter"),
)
internal interface AssignmentFilter {
    fun shouldTrack(assignment: Assignment): Boolean
}

@Deprecated(
    message = "Assignment service is deprecated. Use InMemoryExposureFilter with Exposure service instead.",
    replaceWith = ReplaceWith("InMemoryExposureFilter"),
)
internal class InMemoryAssignmentFilter(size: Int, ttlMillis: Long = DAY_MILLIS) : AssignmentFilter {

    // Cache of canonical assignment to the last sent timestamp.
    private val cache = Cache<String, Unit>(size, ttlMillis)

    override fun shouldTrack(assignment: Assignment): Boolean {
        val canonicalAssignment = assignment.canonicalize()
        val track = cache[canonicalAssignment] == null
        if (track) {
            cache[canonicalAssignment] = Unit
        }
        return track
    }
}
