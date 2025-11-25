package com.amplitude.experiment

import com.amplitude.experiment.cohort.Cohort
import com.amplitude.experiment.cohort.CohortApi
import com.amplitude.experiment.flag.FlagConfigPoller
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlin.system.measureNanoTime
import kotlin.test.AfterTest
import kotlin.test.Test

private const val API_KEY = "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"

class LocalEvaluationClientTest {
    @AfterTest
    fun afterTest() {
        clearAllMocks()
    }

    @Test
    fun `test evaluate, all flags, success`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "test_user"))
        val variant = variants["sdk-local-evaluation-ci-test"]
        Assert.assertEquals(Variant(key = "on", value = "on", payload = "payload"), variant?.copy(metadata = null))
        client.stop()
    }

    @Test
    fun `test evaluate, one flag key, success`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(
            user = ExperimentUser(userId = "test_user"),
            flagKeys = listOf("sdk-local-evaluation-ci-test")
        )
        val variant = variants["sdk-local-evaluation-ci-test"]
        Assert.assertEquals(Variant(key = "on", value = "on", payload = "payload"), variant?.copy(metadata = null))
    }

    @Test
    fun `test evaluate, unknown flag key, success`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(
            user = ExperimentUser(userId = "test_user"),
            flagKeys = listOf("this-flag-doesnt-exit")
        )
        val variant = variants["this-flag-doesnt-exit"]
        Assert.assertNull(variant)
        client.stop()
    }

    @Test
    fun `test evaluate, benchmark 1 flag evaluation`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val duration = measureNanoTime {
            client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
        }
        val millis = duration / 1000.0 / 1000.0
        println("1 flag: $millis")
        Assert.assertTrue(millis < 20)
        client.stop()
    }

    @Test
    fun `test evaluate, benchmark 10 flag evaluations`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        var total = 0L
        repeat(10) {
            total += measureNanoTime {
                client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
            }
        }
        val millis = total / 1000.0 / 1000.0
        println("10 flags: $millis")
        Assert.assertTrue(millis < 40)
        client.stop()
    }

    @Test
    fun `test evaluate, benchmark 100 flag evaluation`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        var total = 0L
        repeat(100) {
            total += measureNanoTime {
                client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
            }
        }
        val millis = total / 1000.0 / 1000.0
        println("100 flags: $millis")
        Assert.assertTrue(millis < 80)
        client.stop()
    }

    @Test
    fun `test evaluate, benchmark 1000 flag evaluation`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        var total = 0L
        repeat(1000) {
            total += measureNanoTime {
                client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
            }
        }
        val millis = total / 1000.0 / 1000.0
        println("1000 flags: $millis")
        Assert.assertTrue(millis < 160)
        client.stop()
    }

    @Test
    fun `test evaluate, with dependencies, should return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "user_id", deviceId = "device_id"))
        val variant = variants["sdk-ci-local-dependencies-test"]
        Assert.assertEquals(variant?.copy(metadata = null), Variant(key = "control", value = "control", payload = null))
        client.stop()
    }

    @Test
    fun `test evaluate, with dependencies and flag keys, should return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(
            ExperimentUser(userId = "user_id", deviceId = "device_id"),
            listOf("sdk-ci-local-dependencies-test")
        )
        val variant = variants["sdk-ci-local-dependencies-test"]
        Assert.assertEquals(variant?.copy(metadata = null), Variant(key = "control", value = "control", payload = null))
        client.stop()
    }

    @Test
    fun `test evaluate, with dependencies and unknown flag keys, should not return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(
            ExperimentUser(userId = "user_id", deviceId = "device_id"),
            listOf("does-not-exist")
        )
        val variant = variants["sdk-ci-local-dependencies-test"]
        Assert.assertEquals(variant, null)
        client.stop()
    }

    @Test
    fun `test evaluate, with dependency holdout exclusion, should not return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "user_id", deviceId = "device_id"))
        val variant = variants["sdk-ci-local-dependencies-test-holdout"]
        Assert.assertEquals(variant, null)
        client.stop()
    }

    @Test
    fun `evaluate with user and group, not targeted`() {
        val cohortConfig = LocalEvaluationConfig(
            cohortSyncConfig = CohortSyncConfig("api", "secret")
        )
        val cohortApi = mockk<CohortApi>().apply {
            every { getCohort(eq("52gz3yi7"), allAny()) } returns Cohort("52gz3yi7", "User", 2, 1722363790000, setOf("1", "2"))
            every { getCohort(eq("mv7fn2bp"), allAny()) } returns Cohort("mv7fn2bp", "User", 1, 1719350216000, setOf("67890", "12345"))
            every { getCohort(eq("s4t57y32"), allAny()) } returns Cohort("s4t57y32", "org name", 1, 1722368285000, setOf("Amplitude Website (Portfolio)"))
            every { getCohort(eq("k1lklnnb"), allAny()) } returns Cohort("k1lklnnb", "org id", 1, 1722466388000, setOf("1"))
        }
        val client = LocalEvaluationClient(API_KEY, cohortConfig, cohortApi = cohortApi)
        client.start()
        val user = ExperimentUser(
            userId = "2333",
            deviceId = "device_id",
            groups = mapOf("org name" to setOf("Amplitude Inc sth sth sth"))
        )
        val userVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-user-cohort-ci-test"))["sdk-local-evaluation-user-cohort-ci-test"]
        assertEquals("off", userVariant?.key)
        assertNull(userVariant?.value)
        val groupVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-group-cohort-ci-test"))["sdk-local-evaluation-group-cohort-ci-test"]
        assertEquals("off", groupVariant?.key)
        assertNull(groupVariant?.value)
        client.stop()
    }

    @Test
    fun `evaluate with user, cohort segment targeted`() {
        val cohortConfig = LocalEvaluationConfig(
            cohortSyncConfig = CohortSyncConfig("api", "secret")
        )
        val cohortApi = mockk<CohortApi>().apply {
            every { getCohort(eq("52gz3yi7"), allAny()) } returns Cohort("52gz3yi7", "User", 2, 1722363790000, setOf("1", "2"))
            every { getCohort(eq("mv7fn2bp"), allAny()) } returns Cohort("mv7fn2bp", "User", 1, 1719350216000, setOf("67890", "12345"))
            every { getCohort(eq("s4t57y32"), allAny()) } returns Cohort("s4t57y32", "org name", 1, 1722368285000, setOf("Amplitude Website (Portfolio)"))
            every { getCohort(eq("k1lklnnb"), allAny()) } returns Cohort("k1lklnnb", "org id", 1, 1722466388000, setOf("1"))
        }
        val client = LocalEvaluationClient(API_KEY, cohortConfig, cohortApi = cohortApi)
        client.start()
        val user = ExperimentUser(
            userId = "12345",
            deviceId = "device_id",
        )
        val userVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-user-cohort-ci-test"))["sdk-local-evaluation-user-cohort-ci-test"]
        assertEquals("on", userVariant?.key)
        assertEquals("on", userVariant?.value)
        client.stop()
    }

    @Test
    fun `evaluate with user, cohort tester targeted`() {
        val cohortConfig = LocalEvaluationConfig(
            cohortSyncConfig = CohortSyncConfig("api", "secret")
        )
        val cohortApi = mockk<CohortApi>().apply {
            every { getCohort(eq("52gz3yi7"), allAny()) } returns Cohort("52gz3yi7", "User", 2, 1722363790000, setOf("1", "2"))
            every { getCohort(eq("mv7fn2bp"), allAny()) } returns Cohort("mv7fn2bp", "User", 1, 1719350216000, setOf("67890", "12345"))
            every { getCohort(eq("s4t57y32"), allAny()) } returns Cohort("s4t57y32", "org name", 1, 1722368285000, setOf("Amplitude Website (Portfolio)"))
            every { getCohort(eq("k1lklnnb"), allAny()) } returns Cohort("k1lklnnb", "org id", 1, 1722466388000, setOf("1"))
        }
        val client = LocalEvaluationClient(API_KEY, cohortConfig, cohortApi = cohortApi)
        client.start()
        val user = ExperimentUser(
            userId = "1",
            deviceId = "device_id",
        )
        val userVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-user-cohort-ci-test"))["sdk-local-evaluation-user-cohort-ci-test"]
        assertEquals("on", userVariant?.key)
        assertEquals("on", userVariant?.value)
        client.stop()
    }

    @Test
    fun `evaluate with group, cohort segment targeted`() {
        val cohortConfig = LocalEvaluationConfig(
            cohortSyncConfig = CohortSyncConfig("api", "secret")
        )
        val cohortApi = mockk<CohortApi>().apply {
            every { getCohort(eq("52gz3yi7"), allAny()) } returns Cohort("52gz3yi7", "User", 2, 1722363790000, setOf("1", "2"))
            every { getCohort(eq("mv7fn2bp"), allAny()) } returns Cohort("mv7fn2bp", "User", 1, 1719350216000, setOf("67890", "12345"))
            every { getCohort(eq("s4t57y32"), allAny()) } returns Cohort("s4t57y32", "org name", 1, 1722368285000, setOf("Amplitude Website (Portfolio)"))
            every { getCohort(eq("k1lklnnb"), allAny()) } returns Cohort("k1lklnnb", "org id", 1, 1722466388000, setOf("1"))
        }
        val client = LocalEvaluationClient(API_KEY, cohortConfig, cohortApi = cohortApi)
        client.start()
        val user = ExperimentUser(
            userId = "2333",
            deviceId = "device_id",
            groups = mapOf("org id" to setOf("1"))

        )
        val groupVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-group-cohort-ci-test"))["sdk-local-evaluation-group-cohort-ci-test"]
        assertEquals("on", groupVariant?.key)
        assertEquals("on", groupVariant?.value)
        client.stop()
    }

    @Test
    fun `evaluate with group, cohort tester targeted`() {
        val cohortConfig = LocalEvaluationConfig(
            cohortSyncConfig = CohortSyncConfig("api", "secret")
        )
        val cohortApi = mockk<CohortApi>().apply {
            every { getCohort(eq("52gz3yi7"), allAny()) } returns Cohort("52gz3yi7", "User", 2, 1722363790000, setOf("1", "2"))
            every { getCohort(eq("mv7fn2bp"), allAny()) } returns Cohort("mv7fn2bp", "User", 1, 1719350216000, setOf("67890", "12345"))
            every { getCohort(eq("s4t57y32"), allAny()) } returns Cohort("s4t57y32", "org name", 1, 1722368285000, setOf("Amplitude Website (Portfolio)"))
            every { getCohort(eq("k1lklnnb"), allAny()) } returns Cohort("k1lklnnb", "org id", 1, 1722466388000, setOf("1"))
        }
        val client = LocalEvaluationClient(API_KEY, cohortConfig, cohortApi = cohortApi)
        client.start()
        val user = ExperimentUser(
            userId = "2333",
            deviceId = "device_id",
            groups = mapOf("org name" to setOf("Amplitude Website (Portfolio)"))

        )
        val groupVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-group-cohort-ci-test"))["sdk-local-evaluation-group-cohort-ci-test"]
        assertEquals("on", groupVariant?.key)
        assertEquals("on", groupVariant?.value)
        client.stop()
    }

    @Test
    fun `test evaluate, stream flags, all flags, success`() {
        mockkConstructor(FlagConfigPoller::class)
        every { anyConstructed<FlagConfigPoller>().start(any()) } answers {
            throw Exception("Should use stream, may be flaky test when stream failed")
        }
        val client = LocalEvaluationClient(API_KEY, LocalEvaluationConfig(streamUpdates = true))
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "test_user"))
        val variant = variants["sdk-local-evaluation-ci-test"]
        Assert.assertEquals(Variant(key = "on", value = "on", payload = "payload"), variant?.copy(metadata = null))
        client.stop()
    }

    @Test
    fun `evaluate with user, stream flags, cohort segment targeted`() {
        mockkConstructor(FlagConfigPoller::class)
        every { anyConstructed<FlagConfigPoller>().start(any()) } answers {
            throw Exception("Should use stream, may be flaky test when stream failed")
        }
        val cohortConfig = LocalEvaluationConfig(
            streamUpdates = true,
            cohortSyncConfig = CohortSyncConfig("api", "secret")
        )
        val cohortApi = mockk<CohortApi>().apply {
            every { getCohort(eq("52gz3yi7"), allAny()) } returns Cohort("52gz3yi7", "User", 2, 1722363790000, setOf("1", "2"))
            every { getCohort(eq("mv7fn2bp"), allAny()) } returns Cohort("mv7fn2bp", "User", 1, 1719350216000, setOf("67890", "12345"))
            every { getCohort(eq("s4t57y32"), allAny()) } returns Cohort("s4t57y32", "org name", 1, 1722368285000, setOf("Amplitude Website (Portfolio)"))
            every { getCohort(eq("k1lklnnb"), allAny()) } returns Cohort("k1lklnnb", "org id", 1, 1722466388000, setOf("1"))
        }
        val client = LocalEvaluationClient(API_KEY, cohortConfig, cohortApi = cohortApi)
        client.start()
        val user = ExperimentUser(
            userId = "12345",
            deviceId = "device_id",
        )
        val userVariant = client.evaluateV2(user, setOf("sdk-local-evaluation-user-cohort-ci-test"))["sdk-local-evaluation-user-cohort-ci-test"]
        assertEquals("on", userVariant?.key)
        assertEquals("on", userVariant?.value)
        client.stop()
    }

    @Test
    fun `evaluateV2 with tracksExposure tracks non-default variants`() {
        val client = LocalEvaluationClient(
            API_KEY,
            LocalEvaluationConfig(
                exposureConfiguration = ExposureConfiguration(apiKey = "apikey")
            )
        )
        client.start()

        // Mock the amplitude client to capture events
        val trackedEvents = mutableListOf<com.amplitude.Event>()
        val exposureService = client.javaClass.getDeclaredField("exposureService").apply {
            isAccessible = true
        }.get(client) as? com.amplitude.experiment.exposure.AmplitudeExposureService

        val amplitudeClient = exposureService?.javaClass?.getDeclaredField("amplitude")?.apply {
            isAccessible = true
        }?.get(exposureService) as? com.amplitude.Amplitude

        val mockAmplitude = mockk<com.amplitude.Amplitude>(relaxed = true)
        every { mockAmplitude.logEvent(any()) } answers {
            val event = firstArg<com.amplitude.Event>()
            trackedEvents.add(event)
        }

        // Replace the amplitude client in the exposure service using reflection
        val amplitudeField = exposureService?.javaClass?.getDeclaredField("amplitude")
        amplitudeField?.isAccessible = true
        val originalAmplitude = amplitudeField?.get(exposureService)
        amplitudeField?.set(exposureService, mockAmplitude)

        try {
            // Perform evaluation with tracksExposure=true
            val options = EvaluateOptions(tracksExposure = true)
            val variants = client.evaluateV2(ExperimentUser(userId = "test_user"), setOf(), options)

            // Verify that track was called
            assert(trackedEvents.isNotEmpty()) { "Expected exposure events to be tracked, but none were tracked" }

            // Count non-default variants
            val nonDefaultVariants = variants.filter { (_, variant) ->
                val isDefault = variant.metadata?.get("default") as? Boolean ?: false
                !isDefault
            }

            // Verify that we have one event per non-default variant
            Assert.assertEquals(nonDefaultVariants.size, trackedEvents.size)

            // Verify each event has the correct structure
            val trackedFlagKeys = mutableSetOf<String>()
            for (event in trackedEvents) {
                assertEquals("[Experiment] Exposure", event.eventType)
                assertEquals("test_user", event.userId)
                val flagKey: String = event.eventProperties?.get("[Experiment] Flag Key") as String
                Assert.assertNotNull(flagKey)
                trackedFlagKeys.add(flagKey)
                // Verify the variant is not default
                val variant = variants[flagKey]
                Assert.assertNotNull(variant)
                val isDefault = variant?.metadata?.get("default") as? Boolean ?: false
                Assert.assertFalse(isDefault)
            }

            // Verify all non-default variants were tracked
            Assert.assertEquals(nonDefaultVariants.keys.size, trackedFlagKeys.size)
            for (flagKey in nonDefaultVariants.keys) {
                Assert.assertTrue(trackedFlagKeys.contains(flagKey))
            }
        } finally {
            // Restore original amplitude client
            amplitudeField?.set(exposureService, originalAmplitude)
            client.stop()
        }
    }
}
