package com.amplitude.experiment.cohort

import com.amplitude.experiment.LocalEvaluationClient
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal const val DEFAULT_MAX_COHORT_SIZE = 15_000
internal const val DEFAULT_SYNC_INTERVAL_SECONDS = 60L

internal data class CohortServiceConfig(
    val maxCohortSize: Int = DEFAULT_MAX_COHORT_SIZE,
    val cohortSyncIntervalSeconds: Long = DEFAULT_SYNC_INTERVAL_SECONDS
)

internal interface CohortService {
    fun start()
    fun stop()
    fun refresh()
    fun manage(cohortIds: Set<String>): Boolean
    fun getCohortsForUser(userId: String): Set<String>
}

internal class PollingCohortService(
    private val config: CohortServiceConfig,
    private val cohortApi: CohortApi,
    private val cohortStorage: CohortStorage,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(),
) : CohortService {

    private val start = Once()
    private val refreshLock = Any()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private val managedCohortsLock = ReentrantReadWriteLock()
    private val managedCohorts = mutableSetOf<String>()

    override fun start() = start.once {
        refresh()
        scheduledExecutor.scheduleWithFixedDelay(
            { refresh() },
            config.cohortSyncIntervalSeconds,
            config.cohortSyncIntervalSeconds,
            TimeUnit.SECONDS
        )
    }

    override fun stop() {
        scheduledExecutor.shutdown()
    }

    override fun getCohortsForUser(userId: String): Set<String> {
        return cohortStorage.getCohortsForUser(userId)
    }

    override fun manage(cohortIds: Set<String>): Boolean = managedCohortsLock.write {
        if (cohortIds != managedCohorts) {
            managedCohorts.clear()
            managedCohorts.addAll(cohortIds)
            true
        } else {
            false
        }
    }

    override fun refresh() = synchronized(refreshLock) {
        Logger.d("Refreshing cohorts")
        val cohortDescriptions = wrapMetrics(
            metric = metrics::onCohortDescriptionsFetch,
            failure = metrics::onCohortDescriptionsFetchFailure,
        ) {
            getCohortDescriptions()
        }
        val filteredCohortDescriptions = filterCohorts(cohortDescriptions)
        val cohortResponses = wrapMetrics(
            metric = metrics::onCohortDownload,
            failure = metrics::onCohortDownloadFailure,
        ) {
            downloadCohorts(filteredCohortDescriptions)
        }
        storeCohorts(cohortResponses)
    }


    internal fun getCohortDescriptions(): List<CohortDescription> {
        Logger.d("Getting cohort descriptions.")
        return cohortApi.getCohorts(GetCohortsRequest).get().cohorts.apply {
            Logger.d("Got cohort descriptions: $this")
        }
    }

    internal fun filterCohorts(networkDescriptions: List<CohortDescription>): List<CohortDescription> {
        Logger.d("Filtering cohorts for download: $networkDescriptions")
        val managedCohorts = managedCohortsLock.read { managedCohorts.toSet() }
        // Filter out cohorts which are (1) not being targeted (2) too large (3) not updated
        return networkDescriptions.filter { networkDescription ->
            val storageDescription = cohortStorage.getCohortDescription(networkDescription.id)
            managedCohorts.contains(networkDescription.id)
                && networkDescription.size <= config.maxCohortSize
                && networkDescription.lastComputed > (storageDescription?.lastComputed ?: -1)
        }.apply {
            Logger.d("Cohorts filtered: $this")
        }
    }

    internal fun downloadCohorts(cohortDescriptions: List<CohortDescription>): List<GetCohortResponse> {
        Logger.d("Downloading cohorts. ${cohortDescriptions.map { it.id }}")
        // Make a request to download each cohort
        return cohortDescriptions.map { description ->
            Logger.d("Downloading cohort ${description.id}")
            cohortApi.getCohort(
                GetCohortRequest(
                    cohortId = description.id,
                    lastComputed = description.lastComputed
                )
            )
        }
            // Handle exceptions and get the response
            .mapNotNull {
                it.handle<GetCohortResponse?> { response, t ->
                    Logger.d("Downloaded cohort ${response?.cohort?.id}")
                    if (response == null || t != null) {
                        Logger.e("get cohort request failed", t)
                        null
                    } else {
                        response
                    }
                }.join()
            }.apply {
                Logger.d("Downloaded cohorts.")
            }
    }

    internal fun storeCohorts(getCohortResponses: List<GetCohortResponse>) {
        Logger.d("Storing cohorts.")
        // Store the cohort included in the response
        getCohortResponses.forEach {
            cohortStorage.putCohort(it.cohort, it.userIds)
        }.apply {
            Logger.d("Cohorts stored.")
        }
    }
}
