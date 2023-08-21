package com.amplitude.experiment.util

import java.util.HashMap

/**
 * Least recently used (LRU) cache with TTL for cache entries.
 */
internal class Cache<K, V>(private val capacity: Int, private val ttlMillis: Long = 0) {

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
    private val timeout = ttlMillis > 0

    init {
        head.next = tail
        tail.prev = head
    }

    operator fun get(key: K): V? = synchronized(lock) {
        val n = map[key] ?: return null
        if (timeout && n.ts + ttlMillis < System.currentTimeMillis()) {
            remove(key)
            return null
        }
        updateInternal(n)
        return n.value
    }

    operator fun set(key: K, value: V) = synchronized(lock) {
        var n = map[key]
        if (n == null) {
            n = Node(key, value)
            map[key] = n
            addInternal(n)
            ++count
        } else {
            n.value = value
            n.ts = System.currentTimeMillis()
            updateInternal(n)
        }
        if (count > capacity) {
            val del = tail.prev?.key
            if (del != null) {
                remove(del)
            }
        }
    }

    fun remove(key: K): Unit = synchronized(lock) {
        val n = map[key] ?: return
        removeInternal(n)
        map.remove(n.key)
        --count
    }

    private fun updateInternal(node: Node<K, V>) {
        removeInternal(node)
        addInternal(node)
    }

    private fun addInternal(node: Node<K, V>) {
        val after = head.next
        head.next = node
        node.prev = head
        node.next = after
        after!!.prev = node
    }

    private fun removeInternal(node: Node<K, V>) {
        val before = node.prev
        val after = node.next
        before!!.next = after
        after!!.prev = before
    }
}