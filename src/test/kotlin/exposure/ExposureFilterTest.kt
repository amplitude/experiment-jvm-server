package com.amplitude.experiment.exposure

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.Variant
import org.junit.Assert
import org.junit.Test

class ExposureFilterTest {

    @Test
    fun `test single exposure`() {
        val filter = InMemoryExposureFilter(100)
        val exposure = Exposure(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure))
    }

    @Test
    fun `test duplicate exposures`() {
        val filter = InMemoryExposureFilter(100)
        val exposure1 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        filter.shouldTrack(exposure1)
        val exposure2 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertFalse(filter.shouldTrack(exposure2))
    }

    @Test
    fun `test same user different results`() {
        val filter = InMemoryExposureFilter(100)
        val exposure1 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure1))
        val exposure2 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to Variant(key = "control"),
                "flag-key-2" to Variant(key = "on"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure2))
    }

    @Test
    fun `test same results for different users`() {
        val filter = InMemoryExposureFilter(100)
        val exposure1 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure1))
        val exposure2 = Exposure(
            ExperimentUser(userId = "different user"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure2))
    }

    @Test
    fun `test empty results`() {
        val filter = InMemoryExposureFilter(100)
        val exposure1 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf()
        )
        Assert.assertTrue(filter.shouldTrack(exposure1))
        val exposure2 = Exposure(
            ExperimentUser(userId = "user"),
            mapOf()
        )
        Assert.assertFalse(filter.shouldTrack(exposure2))
        val exposure3 = Exposure(
            ExperimentUser(userId = "different user"),
            mapOf()
        )
        Assert.assertTrue(filter.shouldTrack(exposure3))
    }

    @Test
    fun `test duplicate exposures with different result ordering`() {
        val filter = InMemoryExposureFilter(100)
        val exposure1 = Exposure(
            ExperimentUser(userId = "user"),
            linkedMapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure1))
        val exposure2 = Exposure(
            ExperimentUser(userId = "user"),
            linkedMapOf(
                "flag-key-2" to Variant(key = "control"),
                "flag-key-1" to Variant(key = "on"),
            )
        )
        Assert.assertFalse(filter.shouldTrack(exposure2))
    }

    @Test
    fun `test lru replacement`() {
        val filter = InMemoryExposureFilter(2)
        val exposure1 = Exposure(
            ExperimentUser(userId = "user1"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure1))
        val exposure2 = Exposure(
            ExperimentUser(userId = "user2"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure2))
        val exposure3 = Exposure(
            ExperimentUser(userId = "user3"),
            mapOf(
                "flag-key-1" to Variant(key = "on"),
                "flag-key-2" to Variant(key = "control"),
            )
        )
        Assert.assertTrue(filter.shouldTrack(exposure3))
        Assert.assertTrue(filter.shouldTrack(exposure1))
    }
}
