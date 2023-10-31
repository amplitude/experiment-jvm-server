package com.amplitude.experiment

import org.junit.Assert
import kotlin.test.Test

class VariantTest {

    // Test Remote

    @Test
    fun `empty json object, decode success`() {
        val jsonString = "{}"
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf<String, Variant>(), variantResponse)
    }

    @Test
    fun `one flag, with key, value missing, payload missing, decode success`() {
        val jsonString = """{"flag":{"key":"key"}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key")), variantResponse)
    }

    @Test
    fun `one flag, with key, null value, null payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","value":null,"payload":null}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key")), variantResponse)
    }

    @Test
    fun `one flag, with key, string value, string payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","value":"value","payload":"payload"}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", value = "value", payload = "payload")), variantResponse)
    }

    @Test
    fun `one flag, with key and value, int payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","value":"value","payload":13121}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", value = "value", payload = 13121)), variantResponse)
    }

    @Test
    fun `one flag, with key and value, boolean payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","value":"value","payload":true}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", value = "value", payload = true)), variantResponse)
    }

    @Test
    fun `one flag, with key and value, object payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","value":"value","payload":{"k":"v"}}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", value = "value", payload = mapOf("k" to "v"))), variantResponse)
    }

    @Test
    fun `one flag, with key and value, array payload, decode success`() {
        val jsonString = """{"flag":{"key":"key","value":"value","payload":["e1","e2"]}}"""
        val variantResponse = parseRemoteResponse(jsonString)
        Assert.assertEquals(mapOf("flag" to Variant(key = "key", value = "value", payload = listOf("e1", "e2"))), variantResponse)
    }
}
