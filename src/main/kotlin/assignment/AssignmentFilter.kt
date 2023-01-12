package com.amplitude.experiment.analytics

import com.amplitude.experiment.util.Murmur3
import java.util.*
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

private const val MAX_SIZE = 1 shl 30
private const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000

internal const val DEFAULT_FILTER_CAPACITY = 65536

internal interface AssignmentFilter {
    fun shouldTrack(assignment: Assignment): Boolean
}

internal fun Assignment.canonicalize(): String {
    val sb = StringBuilder().append(this.user.userId?.trim(), " ", this.user.deviceId?.trim(), " ")
    for (key in this.results.keys.sorted()) {
        val value = this.results[key]
        sb.append(key.trim(), " ", value?.variant?.key?.trim(), " ")
    }
    return sb.toString()
}

internal class LRUAssignmentDedupeService(size: Int) : AssignmentFilter {

    // Cache of canonical assignment to the last sent timestamp.
    private val cache = LRUCache<String, Long>(size)

    override fun shouldTrack(assignment: Assignment): Boolean {
        val now = System.currentTimeMillis()
        val canonicalAssignment = assignment.canonicalize()
        val lastSent = cache[canonicalAssignment]
        return if (lastSent == null || now > lastSent + DAY_MILLIS) {
            cache[canonicalAssignment] = now
            true
        } else {
            false
        }
    }

    internal class LRUCache<K, V>(private val capacity: Int) {

        private class Node<K, V>(
            var key: K? = null,
            var value: V? = null,
            var prev: Node<K, V>? = null,
            var next: Node<K, V>? = null,
        )

        private var count = 0
        private val map: MutableMap<K, Node<K, V>?> = HashMap()
        private val head: Node<K, V> = Node()
        private val tail: Node<K, V> = Node()
        private val lock = Any()

        init {
            head.next = tail
            tail.prev = head
        }

        operator fun get(key: K): V? = synchronized(lock) {
            val n = map[key] ?: return null
            update(n)
            return n.value
        }

        operator fun set(key: K, value: V) = synchronized(lock) {
            var n = map[key]
            if (n == null) {
                n = Node(key, value)
                map[key] = n
                add(n)
                ++count
            } else {
                n.value = value
                update(n)
            }
            if (count > capacity) {
                val toDel = tail.prev
                remove(toDel!!)
                map.remove(toDel.key)
                --count
            }
        }

        private fun update(node: Node<K, V>) {
            remove(node)
            add(node)
        }

        private fun add(node: Node<K, V>) {
            val after = head.next
            head.next = node
            node.prev = head
            node.next = after
            after!!.prev = node
        }

        private fun remove(node: Node<K,V>) {
            val before = node.prev
            val after = node.next
            before!!.next = after
            after!!.prev = before
        }
    }
}

internal class FixedArrayAssignmentDedupeService(size: Int) : AssignmentFilter {
    private val sizeMask: Int
    private val array: AtomicReferenceArray<ByteArray>
    var collisions = 0
    val counts: IntArray

    init {
        require(size > 0) { "array size must be greater than zero, was $size" }
        require(size <= MAX_SIZE) { "array size may not be larger than 2**31-1, but will be rounded to larger. was $size" }
        // round to the next largest power of two
        val poweredSize: Int = 2.0.pow(ceil(ln(size.toDouble()) / ln(2.0))).toInt()
        sizeMask = poweredSize - 1
        println("size = $poweredSize")
        array = AtomicReferenceArray(poweredSize)
        counts = IntArray(poweredSize)
    }

    override fun shouldTrack(assignment: Assignment): Boolean {
        val canonical = assignment.canonicalize().toByteArray(Charsets.UTF_8)
        val code: Int = Murmur3.hash32x86(canonical, canonical.size, 0)

        val index: Int = abs(code) and sizeMask
        counts[index]++
        val old = array.getAndSet(index, canonical)
        if (old != null && !Arrays.equals(canonical, old)) {
            collisions++
        }
        return !Arrays.equals(canonical, old)
    }
}
