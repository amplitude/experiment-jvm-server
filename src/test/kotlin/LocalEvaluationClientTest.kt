@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment

import com.amplitude.experiment.util.LocalEvaluationMetricsCounter
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import org.junit.Assert
import kotlin.system.measureNanoTime
import kotlin.test.Test

private const val API_KEY = "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"

class LocalEvaluationClientTest {

    @Test
    fun `test evaluate, all flags, success`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "test_user"))
        val variant = variants["sdk-local-evaluation-ci-test"]
        Assert.assertEquals(Variant(key = "on", value = "on", payload = "payload"), variant?.copy(metadata = null))
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
    }

    @Test
    fun `test evaluate, with dependencies, should return variant`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val variants = client.evaluate(ExperimentUser(userId = "user_id", deviceId = "device_id"))
        val variant = variants["sdk-ci-local-dependencies-test"]
        Assert.assertEquals(variant?.copy(metadata = null), Variant(key = "control", value = "control", payload = null))
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

    @Test
    fun `test client metrics`() {
        val metrics = LocalEvaluationMetricsCounter()
        val config = LocalEvaluationConfig.builder().apply {
            enableMetrics(metrics)
        }.build()
        val client = LocalEvaluationClient(API_KEY, config)
        client.start()
        client.evaluate(ExperimentUser(userId = "user_id", deviceId = "device_id"))
        Assert.assertEquals(1, metrics.evaluation)
        Assert.assertEquals(1, metrics.flagConfigFetch)
    }
}
