package com.amplitude.experiment.assignment

import com.amplitude.experiment.util.Cache

internal interface AssignmentFilter {
    fun shouldTrack(assignment: Assignment): Boolean
}

internal class InMemoryAssignmentFilter(size: Int, ttlMillis: Long = DAY_MILLIS) : AssignmentFilter {

    // Cache of canonical assignment to the last sent timestamp.
    private val cache = Cache<String, Unit>(size, ttlMillis)

    override fun shouldTrack(assignment: Assignment): Boolean {
        if (assignment.results.isEmpty()) {
            return false
        }
        val canonicalAssignment = assignment.canonicalize()
        val track = cache[canonicalAssignment] == null
        if (track) {
            cache[canonicalAssignment] = Unit
        }
        return track
    }
}
