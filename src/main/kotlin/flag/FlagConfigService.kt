package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.FlagConfig

internal interface FlagConfigService {
    fun start()
    fun stop()
    fun getFlags(keys: List<String> = listOf()): List<FlagConfig>
}
