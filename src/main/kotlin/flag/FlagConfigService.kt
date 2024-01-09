package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal data class FlagConfigServiceConfig(
    val flagConfigPollerIntervalMillis: Long,
)

internal interface FlagConfigService {
    fun start()
    fun stop()
    fun getFlagConfigs(): Map<String, EvaluationFlag>
}

internal class FlagConfigServiceImpl(
    private val config: FlagConfigServiceConfig,
    private val flagConfigApi: FlagConfigApi,
) : FlagConfigService {

    private val lock = Once()
    private val poller = Executors.newSingleThreadScheduledExecutor()

    private val flagConfigsLock = ReentrantReadWriteLock()
    private val flagConfigs: MutableMap<String, EvaluationFlag> = mutableMapOf()

    private fun refresh() {
        Logger.d("Refreshing flag configs.")
        val flagConfigs = fetchFlagConfigs()
        storeFlagConfigs(flagConfigs)
        Logger.d("Refreshed ${flagConfigs.size} flag configs.")
    }

    override fun start() {
        lock.once {
            poller.scheduleAtFixedRate(
                {
                    try {
                        refresh()
                    } catch (e: Exception) {
                        Logger.e("Failed to refresh flag configs.", e)
                    }
                },
                config.flagConfigPollerIntervalMillis,
                config.flagConfigPollerIntervalMillis,
                TimeUnit.MILLISECONDS
            )
            refresh()
        }
    }

    override fun stop() {
        poller.shutdown()
    }

    override fun getFlagConfigs(): Map<String, EvaluationFlag> {
        return flagConfigsLock.read {
            flagConfigs
        }
    }

    private fun fetchFlagConfigs(): List<EvaluationFlag> {
        return flagConfigApi.getFlagConfigs(GetFlagConfigsRequest).get()
    }

    private fun storeFlagConfigs(flagConfigs: List<EvaluationFlag>) {
        flagConfigsLock.write {
            this.flagConfigs.clear()
            this.flagConfigs.putAll(flagConfigs.associateBy { it.key })
        }
    }
}
