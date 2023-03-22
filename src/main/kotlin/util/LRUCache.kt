package com.amplitude.experiment.util

import java.util.HashMap

internal class LRUCache<K, V>(private val capacity: Int, private val ttlMillis: Long = Long.MAX_VALUE) {

    private class Node<K, V>(
        var key: K? = null,
        var value: V? = null,
        var prev: Node<K, V>? = null,
        var next: Node<K, V>? = null,
        var ts: Long = System.currentTimeMillis(),
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
        if (n.ts + ttlMillis < System.currentTimeMillis()) {
            return null
        }
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
            n.ts = System.currentTimeMillis()
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

    private fun remove(node: Node<K, V>) {
        val before = node.prev
        val after = node.next
        before!!.next = after
        after!!.prev = before
    }
}
