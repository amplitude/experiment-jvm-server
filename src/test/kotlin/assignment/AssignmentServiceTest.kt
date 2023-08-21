package com.amplitude.experiment.assignment

import com.amplitude.experiment.ExperimentUser
import org.junit.Assert
import org.junit.Test

class AssignmentServiceTest {

    @Test
    fun `test assignment to amplitude event`() {
        val user = ExperimentUser(userId = "user", deviceId = "device")
        val results = mapOf(
            "flag-key-1" to flagResult(
                variant = "on",
                description = "description-1",
                isDefaultVariant = false
            ),
            "flag-key-2" to flagResult(
                variant = "off",
                description = "description-2",
                isDefaultVariant = true
            ),
        )
        val assignment = Assignment(user, results)
        val event = assignment.toAmplitudeEvent()
        Assert.assertEquals(user.userId, event.userId)
        Assert.assertEquals(user.deviceId, event.deviceId)
        Assert.assertEquals("[Experiment] Assignment", event.eventType)
        val eventProperties = event.eventProperties
        Assert.assertEquals(4, eventProperties.length())
        Assert.assertEquals("on", eventProperties.get("flag-key-1.variant"))
        Assert.assertEquals("description-1", eventProperties.get("flag-key-1.details"))
        Assert.assertEquals("off", eventProperties.get("flag-key-2.variant"))
        Assert.assertEquals("description-2", eventProperties.get("flag-key-2.details"))
        val userProperties = event.userProperties
        Assert.assertEquals(2, userProperties.length())
        Assert.assertEquals(1, userProperties.getJSONObject("\$set").length())
        Assert.assertEquals(1, userProperties.getJSONObject("\$unset").length())
        Assert.assertEquals("on", userProperties.getJSONObject("\$set").get("[Experiment] flag-key-1"))
        Assert.assertEquals("-", userProperties.getJSONObject("\$unset").get("[Experiment] flag-key-2"))
        val canonicalization = "user device flag-key-1 on flag-key-2 off "
        val expected = "user device ${canonicalization.hashCode()} ${assignment.timestamp / DAY_MILLIS}"
        Assert.assertEquals(expected, event.insertId)
    }
}