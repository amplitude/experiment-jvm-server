package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationMode
import com.amplitude.experiment.evaluation.FlagConfig
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

internal typealias FlagConfigInterceptor = (Map<String, FlagConfig>) -> Unit

internal interface FlagConfigService {
    fun start()
    fun stop()
    fun getFlagConfigs(keys: List<String> = listOf()): List<FlagConfig>
    fun addFlagConfigInterceptor(listener: FlagConfigInterceptor)
}

internal class FlagConfigServiceImpl(
    private val config: FlagConfigServiceConfig,
    private val flagConfigApi: FlagConfigApi,
    private val flagConfigStorage: FlagConfigStorage,
) : FlagConfigService {

    private val lock = Once()
    private val poller = Executors.newSingleThreadScheduledExecutor()

    private val interceptorsLock = ReentrantReadWriteLock()
    private val interceptors: MutableSet<FlagConfigInterceptor> = mutableSetOf()

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

    override fun getFlagConfigs(keys: List<String>): List<FlagConfig> {
        return if (keys.isEmpty()) {
            flagConfigStorage.getAll().values.toList()
        } else {
            keys.mapNotNull { flagConfigStorage.get(it) }
        }
    }

    override fun addFlagConfigInterceptor(listener: FlagConfigInterceptor) {
        interceptorsLock.write {
            interceptors += listener
        }
    }

    private fun getFlagConfigs(): Map<String, FlagConfig> {
        return flagConfigApi.getFlagConfigs(GetFlagConfigsRequest(EvaluationMode.LOCAL)).get().apply {
            interceptorsLock.read {
                interceptors.forEach { interceptor ->
                    interceptor.invoke(this)
                }
            }
        }
    }

    private fun storeFlagConfigs(flagConfigs: Map<String, FlagConfig>) {
        flagConfigStorage.clear()
        flagConfigStorage.putAll(flagConfigs)
    }
}
