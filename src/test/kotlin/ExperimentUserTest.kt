package com.amplitude.experiment

import com.amplitude.experiment.ExperimentUser.Companion.builder
import com.amplitude.experiment.util.toJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert
import kotlin.test.Test

class ExperimentUserTest {

    @Test
    fun `user to json`() {
        val user = builder()
            .userId("user_id")
            .deviceId("device_id")
            .country("country")
            .region("region")
            .dma("dma")
            .city("city")
            .language("language")
            .platform("platform")
            .version("version")
            .os("os")
            .deviceManufacturer("device_manufacturer")
            .deviceBrand("device_brand")
            .deviceModel("device_model")
            .carrier("carrier")
            .library("library")
            .userProperty("userPropertyKey", "value")
            .group("group", "group")
            .groupProperty("group", "group", "group", "group")
            .build()

        // Ordering matters here, based on toJson() extension function
        val expected = JsonObject(
            mapOf(
                "user_id" to JsonPrimitive("user_id"),
                "device_id" to JsonPrimitive("device_id"),
                "country" to JsonPrimitive("country"),
                "region" to JsonPrimitive("region"),
                "dma" to JsonPrimitive("dma"),
                "city" to JsonPrimitive("city"),
                "language" to JsonPrimitive("language"),
                "platform" to JsonPrimitive("platform"),
                "version" to JsonPrimitive("version"),
                "os" to JsonPrimitive("os"),
                "device_manufacturer" to JsonPrimitive("device_manufacturer"),
                "device_brand" to JsonPrimitive("device_brand"),
                "device_model" to JsonPrimitive("device_model"),
                "carrier" to JsonPrimitive("carrier"),
                "library" to JsonPrimitive("library"),
                "user_properties" to buildJsonObject {
                    put("userPropertyKey", JsonPrimitive("value"))
                },
                "groups" to buildJsonObject {
                    put("group", buildJsonArray { add("group") })
                },
                "group_properties" to buildJsonObject {
                    put("group", buildJsonObject {
                        put("group", buildJsonObject {
                            put("group", JsonPrimitive("group"))
                        })
                    })
                }
            )
        )
        println(expected.toString())
        println(user.toJson())
        Assert.assertEquals(expected.toString(), user.toJson())
    }
}
