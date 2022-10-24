package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationMode
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal data class FlagConfigServiceConfig(
    val flagConfigPollerIntervalMillis: Long,
)

internal interface FlagConfigService {
    fun start()
    fun stop()
    fun getFlags(keys: List<String> = listOf()): List<FlagConfig>
}

internal class FlagConfigServiceImpl(
    private val config: FlagConfigServiceConfig,
    private val flagConfigApi: FlagConfigApi,
    private val flagConfigStorage: FlagConfigStorage,
) : FlagConfigService {

    private val lock = Once()
    private val poller = Executors.newSingleThreadScheduledExecutor()

    private fun refresh() {
        Logger.d("Refreshing flag configs.")
        val flagConfigs = getFlagConfigs()
        storeFlagConfigs(flagConfigs)
    }

    override fun start() {
        lock.once {
            refresh()
            poller.scheduleAtFixedRate(
                { refresh() },
                config.flagConfigPollerIntervalMillis,
                config.flagConfigPollerIntervalMillis,
                TimeUnit.MILLISECONDS
            )
        }
    }

    override fun stop() {
        poller.shutdown()
    }

    override fun getFlags(keys: List<String>): List<FlagConfig> {
        return if (keys.isEmpty()) {
            flagConfigStorage.getAll().values.toList()
        } else {
            keys.mapNotNull { flagConfigStorage.get(it) }
        }
    }

    private fun getFlagConfigs(): Map<String, FlagConfig> {
        return flagConfigApi.getFlagConfigs(GetFlagConfigsRequest(EvaluationMode.LOCAL)).get()
    }

    private fun storeFlagConfigs(flagConfigs: Map<String, FlagConfig>) {
        flagConfigStorage.clear()
        flagConfigStorage.putAll(flagConfigs)
    }
}
