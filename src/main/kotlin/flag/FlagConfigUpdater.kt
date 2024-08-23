package com.amplitude.experiment.flag

import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.*
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.daemonFactory
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal interface FlagConfigUpdater {
    // Start the updater. There can be multiple calls.
    // If start fails, it should throw exception. The caller should handle fallback.
    // If some other error happened while updating (already started successfully), it should call fallback.
    fun start(fallback: (() -> Unit)? = null)
    // Stop should stop updater temporarily. There may be another start in the future.
    // To stop completely, with intention to never start again, use shutdown() instead.
    fun stop()
    // Destroy should stop the updater forever in preparation for server shutdown.
    fun shutdown()
}

internal abstract class FlagConfigUpdaterBase(
    private val flagConfigStorage: FlagConfigStorage,
    private val cohortLoader: CohortLoader?,
    private val cohortStorage: CohortStorage?,
): FlagConfigUpdater {
    fun update(flagConfigs: List<EvaluationFlag>) {
        println("update")
        // Remove flags that no longer exist.
        val flagKeys = flagConfigs.map { it.key }.toSet()
        flagConfigStorage.removeIf { !flagKeys.contains(it.key) }

        // Get all flags from storage
        val storageFlags = flagConfigStorage.getFlagConfigs()

        // Load cohorts for each flag if applicable and put the flag in storage.
        val futures = ConcurrentHashMap<String, CompletableFuture<*>>()
        for (flagConfig in flagConfigs) {
            if (cohortLoader == null) {
                flagConfigStorage.putFlagConfig(flagConfig)
                continue
            }
            val cohortIds = flagConfig.getAllCohortIds()
            val storageCohortIds = storageFlags[flagConfig.key]?.getAllCohortIds() ?: emptySet()
            val cohortsToLoad = cohortIds - storageCohortIds
            if (cohortsToLoad.isEmpty()) {
                flagConfigStorage.putFlagConfig(flagConfig)
                continue
            }
            for (cohortId in cohortsToLoad) {
                futures.putIfAbsent(
                    cohortId,
                    cohortLoader.loadCohort(cohortId).handle { _, exception ->
                        if (exception != null) {
                            Logger.e("Failed to load cohort $cohortId", exception)
                        }
                        flagConfigStorage.putFlagConfig(flagConfig)
                    }
                )
            }
        }
        futures.values.forEach { it.join() }

        // Delete unused cohorts
        if (cohortStorage != null) {
            val flagCohortIds = flagConfigStorage.getFlagConfigs().values.toList().getAllCohortIds()
            val storageCohortIds = cohortStorage.getCohorts().keys
            val deletedCohortIds = storageCohortIds - flagCohortIds
            for (deletedCohortId in deletedCohortIds) {
                cohortStorage.deleteCohort(deletedCohortId)
            }
        }
        Logger.d("Refreshed ${flagConfigs.size} flag configs.")
    }
}

internal class FlagConfigPoller(
    private val flagConfigApi: FlagConfigApi,
    private val storage: FlagConfigStorage,
    private val cohortLoader: CohortLoader?,
    private val cohortStorage: CohortStorage?,
    private val config: LocalEvaluationConfig,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
): FlagConfigUpdaterBase(
    storage, cohortLoader, cohortStorage
) {
    private val poller = Executors.newScheduledThreadPool(1, daemonFactory)
    private var scheduledFuture: ScheduledFuture<*>? = null
    override fun start(fallback: (() -> Unit)?) {
        // Perform updates
        refresh()
        scheduledFuture = poller.scheduleWithFixedDelay(
            {
                try {
                    refresh()
                } catch (t: Throwable) {
                    Logger.e("Refresh flag configs failed.", t)
                    stop()
                    fallback?.invoke()
                }
            },
            config.flagConfigPollerIntervalMillis,
            config.flagConfigPollerIntervalMillis,
            TimeUnit.MILLISECONDS
        )
    }

    override fun stop() {
        // Pause only stop the task scheduled. It doesn't stop the executor.
        scheduledFuture?.cancel(true)
        scheduledFuture = null
    }

    override fun shutdown() {
        // Stop the executor.
        poller.shutdown()
    }

    fun refresh() {
        Logger.d("Refreshing flag configs.")
        println("flag poller refreshing")
        // Get updated flags from the network.
        val flagConfigs = wrapMetrics(
            metric = metrics::onFlagConfigFetch,
            failure = metrics::onFlagConfigFetchFailure,
        ) {
            flagConfigApi.getFlagConfigs()
        }

        update(flagConfigs)
        println("flag poller refreshed")
    }
}

internal class FlagConfigStreamer(
    private val flagConfigStreamApi: FlagConfigStreamApi,
    private val storage: FlagConfigStorage,
    private val cohortLoader: CohortLoader?,
    private val cohortStorage: CohortStorage?,
    private val config: LocalEvaluationConfig,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
): FlagConfigUpdaterBase(
    storage, cohortLoader, cohortStorage
) {
    override fun start(fallback: (() -> Unit)?) {
        flagConfigStreamApi.onUpdate = {flags ->
            println("flag streamer received")
            update(flags)
        }
        flagConfigStreamApi.onError = {e ->
            Logger.e("Stream flag configs streaming failed.", e)
            metrics.onFlagConfigStreamFailure(e)
            fallback?.invoke()
        }
        wrapMetrics(metric = metrics::onFlagConfigStream, failure = metrics::onFlagConfigStreamFailure) {
            flagConfigStreamApi.connect()
        }
    }

    override fun stop() {
        flagConfigStreamApi.close()
    }

    override fun shutdown() = stop()
}

private const val RETRY_DELAY_MILLIS_DEFAULT = 15 * 1000L
private const val MAX_JITTER_MILLIS_DEFAULT = 2000L
internal class FlagConfigFallbackRetryWrapper(
    private val mainUpdater: FlagConfigUpdater,
    private val fallbackUpdater: FlagConfigUpdater,
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS_DEFAULT,
    private val maxJitterMillis: Long = MAX_JITTER_MILLIS_DEFAULT
): FlagConfigUpdater {
    private val reconnIntervalRange = (retryDelayMillis - maxJitterMillis)..(retryDelayMillis + maxJitterMillis)
    private val executor = Executors.newScheduledThreadPool(1, daemonFactory)
    private var retryTask: ScheduledFuture<*>? = null

    override fun start(fallback: (() -> Unit)?) {
        try {
            mainUpdater.start {
                startRetry(fallback) // Don't care if poller start error or not, always retry.
                try {
                    fallbackUpdater.start(fallback)
                } catch (_: Throwable) {
                    fallback?.invoke()
                }
            }
        } catch (t: Throwable) {
            Logger.e("Update flag configs start failed.", t)
            fallbackUpdater.start(fallback) // If fallback failed, don't retry.
            startRetry(fallback)
        }
    }

    override fun stop() {
        mainUpdater.stop()
        fallbackUpdater.stop()
        retryTask?.cancel(true)
    }

    override fun shutdown() {
        mainUpdater.shutdown()
        fallbackUpdater.shutdown()
        retryTask?.cancel(true)
    }

    private fun startRetry(fallback: (() -> Unit)?) {
        retryTask = executor.schedule({
            try {
                mainUpdater.start {
                    startRetry(fallback) // Don't care if poller start error or not, always retry stream.
                    try {
                        fallbackUpdater.start(fallback)
                    } catch (_: Throwable) {
                        fallback?.invoke()
                    }
                }
                fallbackUpdater.stop()
            } catch (_: Throwable) {
                startRetry(fallback)
            }
        }, reconnIntervalRange.random(), TimeUnit.MILLISECONDS)
    }
}