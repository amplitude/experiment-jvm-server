package com.amplitude.experiment

import com.amplitude.experiment.evaluation.serialization.SerialVariant
import com.amplitude.experiment.util.toVariant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert
import kotlin.test.Test

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

class VariantTest {

    // Test Remote

    @Test
    fun `empty json object, decode success`() {
        val jsonString = "{}"
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf<String, Variant>(), variantResponse)
    }

    @Test
    fun `one flag, with key, payload missing, decode success`() {
        val jsonString = """{"flag":{"key":"key"}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key")), variantResponse)
    }

    @Test
    fun `one flag, with value, null payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":null}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key")), variantResponse)
    }

    @Test
    fun `one flag, with key, string payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":"payload"}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key", "payload")), variantResponse)
    }

    @Test
    fun `one flag, with key, int payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":13121}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key", 13121)), variantResponse)
    }

    @Test
    fun `one flag, with key, boolean payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":true}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key", true)), variantResponse)
    }

    @Test
    fun `one flag, with key, object payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":{"k":"v"}}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key", mapOf("k" to "v"))), variantResponse)
    }

    @Test
    fun `one flag, with key, array payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":["e1","e2"]}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant("key", listOf("e1", "e2"))), variantResponse)
    }

    // Test Local

    @Test
    fun `core variant decode, missing payload, success`() {
        val jsonString = """{"key":"key"}"""
        val original = json.decodeFromString<SerialVariant>(jsonString)
        val core = original.convert()
        val actual = SerialVariant(core).toVariant()
        val expected = Variant("key")
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, string payload, success`() {
        val jsonString = """{"key":"key","payload":"payload"}"""
        val original = json.decodeFromString<SerialVariant>(jsonString)
        val core = original.convert()
        val actual = SerialVariant(core).toVariant()
        val expected = Variant("key", "payload")
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, int payload, success`() {
        val jsonString = """{"key":"key","payload":13121}"""
        val original = json.decodeFromString<SerialVariant>(jsonString)
        val core = original.convert()
        val actual = SerialVariant(core).toVariant()
        val expected = Variant("key", 13121)
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, boolean payload, success`() {
        val jsonString = """{"key":"key","payload":true}"""
        val original = json.decodeFromString<SerialVariant>(jsonString)
        val core = original.convert()
        val actual = SerialVariant(core).toVariant()
        val expected = Variant("key", true)
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, object payload, success`() {
        val jsonString = """{"key":"key","payload":{"k":"v"}}"""
        val original = json.decodeFromString<SerialVariant>(jsonString)
        val core = original.convert()
        val actual = SerialVariant(core).toVariant()
        val expected = Variant("key", mapOf("k" to "v"))
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, array payload, success`() {
        val jsonString = """{"key":"key","payload":["e1","e2"]}"""
        val original = json.decodeFromString<SerialVariant>(jsonString)
        val core = original.convert()
        val actual = SerialVariant(core).toVariant()
        val expected = Variant("key", listOf("e1", "e2"))
        Assert.assertEquals(expected, actual)
    }
}
