package com.amplitude.experiment.flag

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

internal typealias FlagConfigInterceptor = (List<FlagConfig>) -> Unit

internal interface FlagConfigService {
    fun start()
    fun stop()
    fun getFlagConfigs(): List<FlagConfig>
    fun addFlagConfigInterceptor(listener: FlagConfigInterceptor)
}

internal class FlagConfigServiceImpl(
    private val config: FlagConfigServiceConfig,
    private val flagConfigApi: FlagConfigApi,
) : FlagConfigService {

    private val lock = Once()
    private val poller = Executors.newSingleThreadScheduledExecutor()

    private val interceptorsLock = ReentrantReadWriteLock()
    private val interceptors: MutableSet<FlagConfigInterceptor> = mutableSetOf()
    private val flagConfigsLock = ReentrantReadWriteLock()
    private val flagConfigs: MutableList<FlagConfig> = mutableListOf()

    private fun refresh() {
        Logger.d("Refreshing flag configs.")
        val flagConfigs = fetchFlagConfigs()
        storeFlagConfigs(flagConfigs)
        Logger.d("Refreshed ${flagConfigs.size} flag configs.")
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

    override fun getFlagConfigs(): List<FlagConfig> {
        return flagConfigsLock.read {
            flagConfigs
        }
    }

    override fun addFlagConfigInterceptor(listener: FlagConfigInterceptor) {
        interceptorsLock.write {
            interceptors += listener
        }
    }

    private fun fetchFlagConfigs(): List<FlagConfig> {
        return flagConfigApi.getFlagConfigs(GetFlagConfigsRequest).get().apply {
            interceptorsLock.read {
                interceptors.forEach { interceptor ->
                    interceptor.invoke(this)
                }
            }
        }
    }

    private fun storeFlagConfigs(flagConfigs: List<FlagConfig>) {
        flagConfigsLock.write {
            this.flagConfigs.clear()
            this.flagConfigs.addAll(flagConfigs)
        }
    }
}
