package com.amplitude.experiment

import com.amplitude.experiment.analytics.Assignment
import com.amplitude.experiment.analytics.FixedArrayAssignmentDedupeService
import org.junit.Test

class AssignmentDedupeServiceTest {

    @Test
    fun test() {
        val size = 1 shl 10
        val n: Int = size
        val dedupeService = FixedArrayAssignmentDedupeService(size)
        repeat(n) {
            dedupeService.shouldTrack(Assignment(ExperimentUser(userId = "$it"), mapOf()))
        }
        println("${(dedupeService.collisions.toDouble() / n.toDouble()) * 100}%")
        println(dedupeService.counts.contentToString())
    }
}
