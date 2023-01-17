package com.amplitude.experiment.assignment

import com.amplitude.experiment.util.LRUCache

private const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000
internal const val DEFAULT_FILTER_CAPACITY = 65536

internal interface AssignmentFilter {
    fun shouldTrack(assignment: Assignment): Boolean
}

internal fun Assignment.canonicalize(): String {
    val sb = StringBuilder().append(this.user.userId?.trim(), " ", this.user.deviceId?.trim(), " ")
    for (key in this.results.keys.sorted()) {
        val value = this.results[key]
        sb.append(key.trim(), " ", value?.variant?.key?.trim(), " ")
    }
    return sb.toString()
}

internal class LRUAssignmentDedupeService(size: Int) : AssignmentFilter {

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
