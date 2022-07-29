package com.amplitude.experiment

import org.junit.Assert
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.time.measureTime

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
    fun `test evaluate, benchmark 1 flag evaluation`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        val duration = measureNanoTime {
            client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
        }
        val millis = duration / 1000.0 / 1000.0
        Assert.assertTrue(millis < 10)
        println("1 flag: $millis")
    }

    @Test
    fun `test evaluate, benchmark 10 flag evaluations`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        var total = 0L
        repeat(1000) {
            total += measureNanoTime {
                client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
            }
        }
        val millis = total / 1000.0 / 1000.0
        Assert.assertTrue(millis < 20)
        println("10 flags: $millis")
    }

    @Test
    fun `test evaluate, benchmark 100 flag evaluation`() {
        val client = LocalEvaluationClient(API_KEY)
        client.start()
        var total = 0L
        repeat(1000) {
            total += measureNanoTime {
                client.evaluate(ExperimentUser(userId = "test_user"), listOf("sdk-local-evaluation-ci-test"))
            }
        }
        val millis = total / 1000.0 / 1000.0
        Assert.assertTrue(millis < 20)
        println("100 flags: $millis")
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
        Assert.assertTrue(millis < 100)
        println("1000 flags: $millis")
    }
}
