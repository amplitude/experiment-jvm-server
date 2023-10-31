package com.amplitude.experiment.assignment

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.evaluation.EvaluationVariant
import org.junit.Assert
import org.junit.Test

class AssignmentFilterTest {

    @Test
    fun `test single assignment`() {
        val filter = InMemoryAssignmentFilter(100)
        val assignment = Assignment(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment))
    }

    @Test
    fun `test duplicate assignments`() {
        val filter = InMemoryAssignmentFilter(100)
        val assignment1 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        filter.shouldTrack(assignment1)
        val assignment2 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertFalse(filter.shouldTrack(assignment2))
    }

    @Test
    fun `test same user different results`() {
        val filter = InMemoryAssignmentFilter(100)
        val assignment1 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment1))
        val assignment2 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "control"),
                "flag-key-2" to EvaluationVariant(key = "on"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment2))
    }

    @Test
    fun `test same results for different users`() {
        val filter = InMemoryAssignmentFilter(100)
        val assignment1 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment1))
        val assignment2 = Assignment(
            ExperimentUser(userId = "different user"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment2))
    }

    @Test
    fun `test empty results`() {
        val filter = InMemoryAssignmentFilter(100)
        val assignment1 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf()
        )
        Assert.assertTrue(filter.shouldTrack(assignment1))
        val assignment2 = Assignment(
            ExperimentUser(userId = "user"),
            mapOf()
        )
        Assert.assertFalse(filter.shouldTrack(assignment2))
        val assignment3 = Assignment(
            ExperimentUser(userId = "different user"),
            mapOf()
        )
        Assert.assertTrue(filter.shouldTrack(assignment3))
    }

    @Test
    fun `test duplicate assignments with different result ordering`() {
        val filter = InMemoryAssignmentFilter(100)
        val assignment1 = Assignment(
            ExperimentUser(userId = "user"),
            linkedMapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment1))
        val assignment2 = Assignment(
            ExperimentUser(userId = "user"),
            linkedMapOf(
                "flag-key-2" to EvaluationVariant(key = "control"),
                "flag-key-1" to EvaluationVariant(key = "on"),
            )
        )
        Assert.assertFalse(filter.shouldTrack(assignment2))
    }

    @Test
    fun `test lru replacement`() {
        val filter = InMemoryAssignmentFilter(2)
        val assignment1 = Assignment(
            ExperimentUser(userId = "user1"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment1))
        val assignment2 = Assignment(
            ExperimentUser(userId = "user2"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment2))
        val assignment3 = Assignment(
            ExperimentUser(userId = "user3"),
            mapOf(
                "flag-key-1" to EvaluationVariant(key = "on"),
                "flag-key-2" to EvaluationVariant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(assignment3))
        Assert.assertTrue(filter.shouldTrack(assignment1))
    }
}
