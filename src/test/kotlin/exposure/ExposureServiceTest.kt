package com.amplitude.experiment.exposure

import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.Variant
import org.junit.Assert
import org.junit.Test

class ExposureServiceTest {

    @Test
    fun `test exposure to amplitude event`() {
        val user = ExperimentUser(userId = "user", deviceId = "device")
        val results = mapOf(
            "flag-key-1" to Variant(
                key = "on",
                value = "on",
                metadata = mapOf(
                    "segmentName" to "Segment",
                    "flagVersion" to 13,
                )
            ),
            "flag-key-2" to Variant(
                key = "off",
                metadata = mapOf(
                    "segmentName" to "All Other Users",
                    "flagVersion" to 12,
                    "default" to true,
                )
            ),
            "empty-variant" to Variant()
        )
        val exposure = Exposure(user, results)
        val events = exposure.toAmplitudeEvent()
        Assert.assertEquals(2, events.size)
        for (event in events) {
            Assert.assertEquals(user.userId, event.userId)
            Assert.assertEquals(user.deviceId, event.deviceId)
            Assert.assertEquals("[Experiment] Exposure", event.eventType)

            val flagKey = event.eventProperties?.getString("[Experiment] Flag Key")
            Assert.assertEquals(results[flagKey]?.key, if (event.eventProperties.has("[Experiment] Variant")) event.eventProperties.getString("[Experiment] Variant") else null)

            val userProperties = event.userProperties
            Assert.assertEquals(2, userProperties.length())
            if (results[flagKey]?.key != null || results[flagKey]?.value != null) {
                Assert.assertEquals(1, userProperties.getJSONObject("\$set").length())
                Assert.assertEquals(
                    results[flagKey]?.key,
                    userProperties.getJSONObject("\$set").get("[Experiment] $flagKey")
                )
            } else {
                Assert.assertEquals(0, userProperties.getJSONObject("\$set").length())
                Assert.assertFalse(userProperties.getJSONObject("\$set").has("[Experiment] $flagKey"))
            }
            Assert.assertEquals(0, userProperties.getJSONObject("\$unset").length())

            val canonicalization = "user device empty-variant null flag-key-1 on flag-key-2 off "
            val expected = "user device ${("$flagKey $canonicalization").hashCode()} ${exposure.timestamp / DAY_MILLIS}"
            Assert.assertEquals(expected, event.insertId)
        }
    }
}
