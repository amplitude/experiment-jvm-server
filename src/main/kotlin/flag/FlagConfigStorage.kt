package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationFlag
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface FlagConfigStorage {
    fun getFlagConfigs(): Map<String, EvaluationFlag>
    fun putFlagConfigs(flagConfigs: Map<String, EvaluationFlag>)
}

internal class InMemoryFlagConfigStorage : FlagConfigStorage {

    private val flagConfigs = mutableMapOf<String, EvaluationFlag>()
    private val flagConfigsLock = ReentrantReadWriteLock()

    override fun getFlagConfigs(): Map<String, EvaluationFlag> {
        return flagConfigsLock.read { flagConfigs.toMap() }
    }

    override fun putFlagConfigs(flagConfigs: Map<String, EvaluationFlag>) {
        return flagConfigsLock.write {
            this.flagConfigs.clear()
            this.flagConfigs.putAll(flagConfigs)
        }
    }
}
