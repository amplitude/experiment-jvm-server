package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger

class VariantTest {

    init {
        Logger.implementation = SystemLogger(true)
    }

    // TODO test variant conversions with payloads in particular
//    @Test
//    fun `empty json object to variant`() {
//        val jsonObject = JSONObject()
//        val variant = jsonObject.toVariant()
//        Assert.assertNull(variant)
//    }
//
//    @Test
//    fun `json object to variant`() {
//        val jsonObject = JSONObject()
//        jsonObject.put("value", "value")
//        jsonObject.put("payload", "payload")
//        val variant = jsonObject.toVariant()
//        Assert.assertNotNull(variant)
//        Assert.assertEquals("value", variant!!.value)
//        Assert.assertEquals("payload", variant.payload)
//    }
//
//    @Test
//    fun `json object to variant deprecated field`() {
//        val jsonObject = JSONObject()
//        jsonObject.put("key", "value")
//        val variant = jsonObject.toVariant()
//        Assert.assertNotNull(variant)
//        Assert.assertEquals("value", variant!!.value)
//        Assert.assertNull(variant.payload)
//    }
//
//    @Test
//    fun `variant to json object`() {
//        run {
//            val variant = Variant("value", null)
//            val jsonObject = JSONObject()
//            jsonObject.put("value", "value")
//            Assert.assertEquals(jsonObject.toString(), variant.toJson())
//        }
//    }
}
