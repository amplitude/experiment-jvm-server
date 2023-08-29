package com.amplitude.experiment

import org.junit.Assert
import kotlin.test.Test

private const val API_KEY = "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"

class LocalEvaluationClientTest {

    @Test
    fun `test evaluate, all flags, success`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "test_user"))
        val variant = variants["sdk-local-evaluation-ci-test"]
        Assert.assertEquals(variant, Variant(value = "on", payload = "payload"))
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
        Assert.assertEquals(variant, Variant(value = "on", payload = "payload"))
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
    }

    @Test
    fun `test evaluate, with dependencies, should return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "user_id", deviceId = "device_id"))
        val variant = variants["sdk-ci-local-dependencies-test"]
        Assert.assertEquals(variant, Variant(value = "control", payload = null))
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
        Assert.assertEquals(variant, Variant(value = "control", payload = null))
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
    }

    @Test
    fun `test evaluate, with dependency holdout exclusion, should not return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "user_id", deviceId = "device_id"))
        val variant = variants["sdk-ci-local-dependencies-test-holdout"]
        Assert.assertEquals(variant, null)
    }
}
