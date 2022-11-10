package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.FlagConfig
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface FlagConfigStorage {
    fun get(flagKey: String): FlagConfig?
    fun getAll(): Map<String, FlagConfig>
    fun overwrite(flagConfigs: Map<String, FlagConfig>)
}

internal class InMemoryFlagConfigStorage : FlagConfigStorage {

    private val lock = ReentrantReadWriteLock()
    private val flagConfigStore = mutableMapOf<String, FlagConfig>()

    override fun get(flagKey: String): FlagConfig? {
        lock.read {
            return flagConfigStore[flagKey]
        }
    }

    override fun getAll(): Map<String, FlagConfig> {
        lock.read {
            return flagConfigStore.toMap()
        }
    }

    override fun overwrite(flagConfigs: Map<String, FlagConfig>) {
        lock.write {
            flagConfigStore.clear()
            flagConfigStore.putAll(flagConfigs)
        }
    }
}
