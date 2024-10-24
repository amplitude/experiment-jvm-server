package com.amplitude.experiment.flag

import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.CohortTooLargeException
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.daemonFactory
import com.amplitude.experiment.util.getAllCohortIds
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Flag config updaters should receive flags through their own means (ex. http GET, SSE stream),
 * or as wrapper of others.
 * They all should have these methods to control their lifecycle.
 */
internal interface FlagConfigUpdater {
    /**
     * Start the updater. There can be multiple calls.
     * If start fails, it should throw exception. The caller should handle error.
     * If some other error happened while updating (already started successfully), it should call onError.
     */
    fun start(onError: (() -> Unit)? = null)

    /**
     * Stop should stop updater temporarily. There may be another start in the future.
     * To stop completely, with intention to never start again, use shutdown() instead.
     */
    fun stop()

    /**
     * Destroy should stop the updater forever in preparation for server shutdown.
     */
    fun shutdown()
}

/**
 * All flag config updaters should share this class, which contains a function to properly process flag updates.
 */
internal abstract class FlagConfigUpdaterBase(
    private val flagConfigStorage: FlagConfigStorage,
    private val cohortLoader: CohortLoader?,
    private val cohortStorage: CohortStorage?,
) {
    /**
     * Call this method after receiving and parsing flag configs from network.
     * This method updates flag configs into storage and download all cohorts if needed.
     */
    protected fun update(flagConfigs: List<EvaluationFlag>) {
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
                        if (exception is CohortTooLargeException) {
                            Logger.w("Failed to load cohort $cohortId", exception)
                        } else if (exception != null) {
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

/**
 * This is the poller for flag configs.
 * It keeps polling flag configs with specified interval until error occurs.
 */
internal class FlagConfigPoller(
    private val flagConfigApi: FlagConfigApi,
    storage: FlagConfigStorage,
    cohortLoader: CohortLoader?,
    cohortStorage: CohortStorage?,
    private val config: LocalEvaluationConfig,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) : FlagConfigUpdater, FlagConfigUpdaterBase(
    storage, cohortLoader, cohortStorage
) {
    private val lock: ReentrantLock = ReentrantLock()
    private val pool = Executors.newScheduledThreadPool(1, daemonFactory)
    private var scheduledFuture: ScheduledFuture<*>? = null // @GuardedBy(lock)

    /**
     * Start will fetch once, then start poller to poll flag configs.
     */
    override fun start(onError: (() -> Unit)?) {
        refresh()
        lock.withLock {
            stopInternal()
            scheduledFuture = pool.scheduleWithFixedDelay(
                {
                    try {
                        refresh()
                    } catch (t: Throwable) {
                        Logger.e("Refresh flag configs failed.", t)
                        stop()
                        onError?.invoke()
                    }
                },
                config.flagConfigPollerIntervalMillis,
                config.flagConfigPollerIntervalMillis,
                TimeUnit.MILLISECONDS
            )
        }
    }

    // @GuardedBy(lock)
    private fun stopInternal() {
        // Pause only stop the task scheduled. It doesn't stop the executor.
        scheduledFuture?.cancel(true)
        scheduledFuture = null
    }

    override fun stop() {
        lock.withLock {
            stopInternal()
        }
    }

    override fun shutdown() {
        lock.withLock {
            // Stop the executor.
            pool.shutdown()
        }
    }

    private fun refresh() {
        Logger.d("Refreshing flag configs.")
        // Get updated flags from the network.
        val flagConfigs = wrapMetrics(
            metric = metrics::onFlagConfigFetch,
            failure = metrics::onFlagConfigFetchFailure,
        ) {
            flagConfigApi.getFlagConfigs()
        }

        update(flagConfigs)
    }
}

/**
 * Streamer for flag configs. This receives flag updates with an SSE connection.
 */
internal class FlagConfigStreamer(
    private val flagConfigStreamApi: FlagConfigStreamApi,
    storage: FlagConfigStorage,
    cohortLoader: CohortLoader?,
    cohortStorage: CohortStorage?,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : FlagConfigUpdater, FlagConfigUpdaterBase(
    storage, cohortLoader, cohortStorage
) {
    private val lock: ReentrantLock = ReentrantLock()

    /**
     * Start makes sure it connects to stream and the first set of flag configs is loaded.
     * Then, it will update the flags whenever there's a stream.
     */
    override fun start(onError: (() -> Unit)?) {
        lock.withLock {
            val onStreamUpdate: ((List<EvaluationFlag>) -> Unit) = { flags ->
                update(flags)
            }
            val onStreamError: ((Exception?) -> Unit) = { e ->
                Logger.e("Stream flag configs streaming failed.", e)
                metrics.onFlagConfigStreamFailure(e)
                onError?.invoke()
            }
            wrapMetrics(metric = metrics::onFlagConfigStream, failure = metrics::onFlagConfigStreamFailure) {
                flagConfigStreamApi.connect(onStreamUpdate, onStreamUpdate, onStreamError)
            }
        }
    }

    override fun stop() {
        // Not guarded by lock. close() can cancel start().
        flagConfigStreamApi.close()
    }

    override fun shutdown() = stop()
}

private const val RETRY_DELAY_MILLIS_DEFAULT = 15 * 1000L
private const val MAX_JITTER_MILLIS_DEFAULT = 2000L

/**
 * This is a wrapper class around flag config updaters.
 * This provides retry capability in case errors encountered during update asynchronously, as well as fallbacks when an updater failed.
 *
 * `mainUpdater` cannot be a FlagConfigFallbackRetryWrapper.
 * The developer should restructure arguments to make sure `mainUpdater` is never a `FlagConfigFallbackRetryWrapper`.
 * All retry and fallback structures can be normalized into `mainUpdater`s not being `FlagConfigFallbackRetryWrapper`s.
 */
internal class FlagConfigFallbackRetryWrapper(
    private val mainUpdater: FlagConfigUpdater,
    private val fallbackUpdater: FlagConfigUpdater?,
    retryDelayMillis: Long = RETRY_DELAY_MILLIS_DEFAULT,
    maxJitterMillis: Long = MAX_JITTER_MILLIS_DEFAULT,
) : FlagConfigUpdater {
    private val lock: ReentrantLock = ReentrantLock()
    private val reconnIntervalRange = max(0, retryDelayMillis - maxJitterMillis)..(min(retryDelayMillis, Long.MAX_VALUE - maxJitterMillis) + maxJitterMillis)
    private val executor = Executors.newScheduledThreadPool(1, daemonFactory)
    private var retryTask: ScheduledFuture<*>? = null // @GuardedBy(lock)

    /**
     * Since the wrapper retries for mainUpdater, so there will never be error case. Thus, onError will never be called.
     *
     * During start, the wrapper tries to start main updater.
     *   If main start success, start success.
     *   If main start failed, fallback updater tries to start.
     *     If fallback start failed as well, throws exception.
     *     If fallback start success, start success, main enters retry loop.
     * After started, if main failed, fallback is started and main enters retry loop.
     *   Fallback success or failures status is not monitored. It's suggested to wrap fallback into a retry wrapper.
     */
    override fun start(onError: (() -> Unit)?) {
        if (mainUpdater is FlagConfigFallbackRetryWrapper) {
            throw Error("Do not use FlagConfigFallbackRetryWrapper as main updater. Fallback updater will never be used. Rewrite retry and fallback logic.")
        }

        lock.withLock {
            retryTask?.cancel(true)

            try {
                mainUpdater.start {
                    lock.withLock {
                        scheduleRetry() // Don't care if poller start error or not, always retry.
                        try {
                            fallbackUpdater?.start()
                        } catch (_: Throwable) {
                        }
                    }
                }
                fallbackUpdater?.stop()
            } catch (t: Throwable) {
                Logger.e("Primary flag configs start failed, start fallback. Error: ", t)
                if (fallbackUpdater == null) {
                    // No fallback, main start failed is wrapper start fail
                    throw t
                }
                fallbackUpdater.start()
                scheduleRetry()
            }
        }
    }

    override fun stop() {
        lock.withLock {
            mainUpdater.stop()
            fallbackUpdater?.stop()
            retryTask?.cancel(true)
        }
    }

    override fun shutdown() {
        lock.withLock {
            mainUpdater.shutdown()
            fallbackUpdater?.shutdown()
            retryTask?.cancel(true)
        }
    }

    // @GuardedBy(lock)
    private fun scheduleRetry() {
        retryTask = executor.schedule({
            try {
                mainUpdater.start {
                    scheduleRetry() // Don't care if poller start error or not, always retry stream.
                    try {
                        fallbackUpdater?.start()
                    } catch (_: Throwable) {
                    }
                }
                fallbackUpdater?.stop()
            } catch (_: Throwable) {
                scheduleRetry()
            }
        }, reconnIntervalRange.random(), TimeUnit.MILLISECONDS)
        }
    }
    