package com.amplitude.experiment.assignment

import com.amplitude.experiment.util.Cache

internal interface AssignmentFilter {
    fun shouldTrack(assignment: Assignment): Boolean
}

internal class InMemoryAssignmentFilter(size: Int) : AssignmentFilter {

    // Cache of canonical assignment to the last sent timestamp.
    private val cache = Cache<String, Unit>(size, DAY_MILLIS)

    override fun shouldTrack(assignment: Assignment): Boolean {
        val canonicalAssignment = assignment.canonicalize()
        val track = cache[canonicalAssignment] == null
        if (track) {
            cache.remove(canonicalAssignment)
        }
        return track
    }
}
