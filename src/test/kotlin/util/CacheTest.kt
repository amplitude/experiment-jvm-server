package com.amplitude.experiment.util

import org.junit.Assert
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future

class CacheTest {

    @Test
    fun `test get no entry`() {
        val cache = Cache<Int, Int>(4)
        val value = cache[0]
        Assert.assertNull(value)
    }

    @Test
    fun `test set and get`() {
        val cache = Cache<Int, Int>(4)
        cache[0] = 0
        val value = cache[0]
        Assert.assertEquals(0, value)
    }

    @Test
    fun `test least recently used entry is removed`() {
        val cache = Cache<Int, Int>(4)
        repeat(4) { i ->
            cache[i] = i
        }
        cache[4] = 4
        val value = cache[0]
        Assert.assertNull(value)
    }

    @Test
    fun `test first set then get entry is not removed`() {
        val cache = Cache<Int, Int>(4)
        repeat(4) { i ->
            cache[i] = i
        }
        val expectedValue = cache[0]
        cache[4] = 4
        val actualValue = cache[0]
        Assert.assertEquals(expectedValue, actualValue)
        val removedValue = cache[1]
        Assert.assertNull(removedValue)
    }

    @Test
    fun `test first set then re-set entry is not removed`() {
        val cache = Cache<Int, Int>(4)
        repeat(4) { i ->
            cache[i] = i
        }
        cache[0] = 0
        cache[4] = 4
        val actualValue = cache[0]
        Assert.assertEquals(0, actualValue)
        val removedValue = cache[1]
        Assert.assertNull(removedValue)
    }

    @Test
    fun `test first set then re-set with different value entry is not removed`() {
        val cache = Cache<Int, Int>(4)
        repeat(4) { i ->
            cache[i] = i
        }
        cache[0] = 100
        cache[4] = 4
        val actualValue = cache[0]
        Assert.assertEquals(100, actualValue)
        val removedValue = cache[1]
        Assert.assertNull(removedValue)
    }

    @Test
    fun `test concurrent access`() {
        val n = 100
        val executor = Executors.newFixedThreadPool(n)
        val cache = Cache<Int, Int>(n)
        val futures = mutableListOf<Future<*>>()
        repeat(n) { i ->
            futures.add(
                executor.submit {
                    cache[i] = i
                }
            )
        }
        futures.forEach { f -> f.get() }
        repeat(n) { i ->
            Assert.assertEquals(i, cache[i])
        }
        futures.clear()
        val k = 50
        repeat(k) { i ->
            futures.add(
                executor.submit {
                    cache[i + k] = i + k
                }
            )
        }
        futures.forEach { f -> f.get() }
        repeat(k) { i ->
            Assert.assertEquals(i, cache[i])
            Assert.assertEquals(i + k, cache[i + k])
        }
    }
}
