package com.amplitude.experiment

import com.amplitude.experiment.evaluation.EvaluationVariant
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.DefaultLogger
import com.amplitude.experiment.util.LogLevel
import com.amplitude.experiment.util.json
import com.amplitude.experiment.util.toVariant
import org.junit.Assert
import kotlin.test.Test

class VariantTest {

    init {
        Logger.configure(LogLevel.DEBUG)
    }

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
        Assert.assertEquals(mapOf("flag" to Variant(key = "key")), variantResponse)
    }

    @Test
    fun `one flag, with value, null payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":null}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key")), variantResponse)
    }

    @Test
    fun `one flag, with key, string payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":"payload"}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", payload = "payload")), variantResponse)
    }

    @Test
    fun `one flag, with key, int payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":13121}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", payload = 13121)), variantResponse)
    }

    @Test
    fun `one flag, with key, boolean payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":true}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", payload = true)), variantResponse)
    }

    @Test
    fun `one flag, with key, object payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":{"k":"v"}}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", payload = mapOf("k" to "v"))), variantResponse)
    }

    @Test
    fun `one flag, with key, array payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","payload":["e1","e2"]}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", payload = listOf("e1", "e2"))), variantResponse)
    }

    // Test Local

    @Test
    fun `core variant decode, missing payload, success`() {
        val jsonString = """{"key":"key"}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key")
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, string payload, success`() {
        val jsonString = """{"key":"key","payload":"payload"}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key", payload = "payload")
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, int payload, success`() {
        val jsonString = """{"key":"key","payload":13121}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key", payload = 13121)
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, boolean payload, success`() {
        val jsonString = """{"key":"key","payload":true}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key", payload = true)
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, object payload, success`() {
        val jsonString = """{"key":"key","payload":{"k":"v"}}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key", payload = mapOf("k" to "v"))
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `core variant decode, array payload, success`() {
        val jsonString = """{"key":"key","payload":["e1","e2"]}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key", payload = listOf("e1", "e2"))
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun `variant decode all fields`() {
        val jsonString = """{"key":"key","value":"value","payload":"payload","metadata":{"default":true}}"""
        val actual = json.decodeFromString<EvaluationVariant>(jsonString).toVariant()
        val expected = Variant(key = "key", value = "value", payload = "payload", metadata = mapOf("default" to true))
        Assert.assertEquals(expected, actual)
    }
}
