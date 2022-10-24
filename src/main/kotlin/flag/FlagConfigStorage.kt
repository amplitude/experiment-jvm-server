package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.FlagConfig
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface FlagConfigStorage {
    /**
     * Get a flag configuration for the given flag key from the cache.
     *
     * @param flagKey the key to get the flag configuration for
     */
    fun get(flagKey: String): FlagConfig?

    /**
     * Get all the flag configurations from the cache.
     */
    fun getAll(): Map<String, FlagConfig>

    /**
     * Put a flag configuration in the cache, overwriting an existing
     * configuration for the same flag key.
     *
     * @param flagKey The flag key for the given flag configuration.
     * @param flagConfig The flag configuration to store in the cache.
     */
    fun put(flagKey: String, flagConfig: FlagConfig)

    /**
     * Put all the flag configurations into the cache, overwriting any existing
     * configurations for the same key, but not clearing the cache completely.
     *
     * @param flagConfigs The flag keys and configurations to put into the cache.
     */
    fun putAll(flagConfigs: Map<String, FlagConfig>)

    /**
     * Delete a flag key and configuration from the cache.
     *
     * @param flagKey The key and configuration to delete.
     */
    fun delete(flagKey: String)

    /**
     * Clear the cache of all flag keys and configurations.
     */
    fun clear()
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

    override fun put(flagKey: String, flagConfig: FlagConfig) {
        lock.write {
            flagConfigStore[flagKey] = flagConfig
        }
    }

    override fun putAll(flagConfigs: Map<String, FlagConfig>) {
        lock.write {
            flagConfigStore.putAll(flagConfigs)
        }
    }

    override fun delete(flagKey: String) {
        lock.write {
            flagConfigStore.remove(flagKey)
        }
    }

    override fun clear() {
        lock.write {
            flagConfigStore.clear()
        }
    }
}
