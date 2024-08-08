package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationFlag
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface FlagConfigStorage {
    fun getFlagConfigs(): Map<String, EvaluationFlag>
    fun putFlagConfig(flagConfig: EvaluationFlag)
    fun removeIf(condition: (EvaluationFlag) -> Boolean)
    fun update(put: List<EvaluationFlag>, remove: List<EvaluationFlag>)
}

internal class InMemoryFlagConfigStorage : FlagConfigStorage {

    private val flagConfigs = mutableMapOf<String, EvaluationFlag>()
    private val flagConfigsLock = ReentrantReadWriteLock()

    override fun getFlagConfigs(): Map<String, EvaluationFlag> {
        return flagConfigsLock.read { flagConfigs.toMap() }
    }

    override fun putFlagConfig(flagConfig: EvaluationFlag) {
        flagConfigsLock.write {
            flagConfigs.put(flagConfig.key, flagConfig)
        }
    }

    override fun removeIf(condition: (EvaluationFlag) -> Boolean) {
        return flagConfigsLock.write {
            flagConfigs.entries.removeIf { condition(it.value) }
        }
    }

    override fun update(put: List<EvaluationFlag>, remove: List<EvaluationFlag>) {

        return flagConfigsLock.write {
            flagConfigs.putAll(put.associateBy { it.key })
            remove.forEach { flagConfigs.remove(it.key) }
        }
    }
}
