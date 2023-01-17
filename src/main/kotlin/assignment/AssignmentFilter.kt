package com.amplitude.experiment.assignment

import com.amplitude.experiment.util.LRUCache

internal const val DEFAULT_FILTER_CAPACITY = 65536

internal interface AssignmentFilter {
    fun shouldTrack(assignment: Assignment): Boolean
}

internal class LRUAssignmentFilter(size: Int) : AssignmentFilter {

    // Cache of canonical assignment to the last sent timestamp.
    private val cache = LRUCache<String, Long>(size)

    override fun shouldTrack(assignment: Assignment): Boolean {
        val now = System.currentTimeMillis()
        val canonicalAssignment = assignment.canonicalize()
        val lastSent = cache[canonicalAssignment]
        return if (lastSent == null || now > lastSent + DAY_MILLIS) {
            cache[canonicalAssignment] = now
            true
        } else {
            false
        }
    }
}
