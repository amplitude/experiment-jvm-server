package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.FlagConfig
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface FlagConfigStorage {
    fun getFlagConfigs(): List<FlagConfig>
    fun putFlagConfigs(flagConfigs: List<FlagConfig>)
}

internal class InMemoryFlagConfigStorage : FlagConfigStorage {

    private val flagConfigs = mutableListOf<FlagConfig>()
    private val flagConfigsLock = ReentrantReadWriteLock()

    override fun getFlagConfigs(): List<FlagConfig> {
        return flagConfigsLock.read { flagConfigs.toList() }
    }

    override fun putFlagConfigs(flagConfigs: List<FlagConfig>) {
        return flagConfigsLock.write {
            this.flagConfigs.clear()
            this.flagConfigs.addAll(flagConfigs)
        }
    }
}
