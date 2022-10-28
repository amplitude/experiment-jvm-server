package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal const val DEFAULT_MAX_COHORT_SIZE = 15_000
internal const val DEFAULT_SYNC_INTERVAL_SECONDS = 60L

internal data class CohortServiceConfig(
    val maxCohortSize: Int = DEFAULT_MAX_COHORT_SIZE,
    val cohortSyncIntervalSeconds: Long = DEFAULT_SYNC_INTERVAL_SECONDS
)

internal interface CohortService {
    fun start()
    fun stop()
    fun refresh(cohortIds: Set<String> = setOf())
    fun getCohorts(userId: String): Set<String>
}

internal class CohortServiceImpl(
    private val config: CohortServiceConfig,
    private val cohortApi: CohortApi,
    private val cohortStorage: CohortStorage,
    private val cohortIdProvider: CohortIdProvider,
) : CohortService {

    private val lock = Once()
    private val poller = Executors.newSingleThreadScheduledExecutor()

    override fun refresh(cohortIds: Set<String>) {
        Logger.d("Refreshing cohorts $cohortIds")
        getCohortDescriptions()
            .filterCohorts(cohortIds)
            .downloadCohorts()
            .storeCohorts()
    }

    override fun start() {
        lock.once {
            refresh()
            poller.scheduleWithFixedDelay(
                { refresh() },
                config.cohortSyncIntervalSeconds,
                config.cohortSyncIntervalSeconds,
                TimeUnit.SECONDS
            )
        }
    }

    override fun stop() {
        poller.shutdown()
    }

    override fun getCohorts(userId: String): Set<String> {
        return cohortStorage.getCohortsForUser(userId)
    }

    internal fun getCohortDescriptions(): List<CohortDescription> {
        Logger.d("Getting cohort descriptions.")
        return cohortApi.getCohorts(GetCohortsRequest).get().cohorts.apply {
            Logger.d("Got cohort descriptions: $this")
        }
    }

    private fun List<CohortDescription>.filterCohorts(cohortIds: Set<String>): List<CohortDescription> =
        filterCohorts(this, cohortIds)

    internal fun filterCohorts(cohortDescriptions: List<CohortDescription>, cohortIds: Set<String> = setOf()): List<CohortDescription> {
        // Filter for explicit cohort ids, otherwise use the cohort ID provider.
        val includedCohortIds = cohortIds.ifEmpty {
            cohortIdProvider.invoke()
        }
        Logger.d("Filtering cohorts for download: $includedCohortIds")
        // Filter out cohorts which are (1) not being targeted (2) too large (3) not updated
        return cohortDescriptions.filter { inputDescription ->
            val storageDescription = cohortStorage.getCohortDescription(inputDescription.id)
            Logger.d("${inputDescription.id} storage last computed = ${storageDescription?.lastComputed}, network last computed = ${inputDescription.lastComputed}")
            includedCohortIds.contains(inputDescription.id) &&
                inputDescription.size < config.maxCohortSize &&
                inputDescription.lastComputed > (storageDescription?.lastComputed ?: 0)
        }.apply {
            Logger.d("Cohorts filtered: $this")
        }
    }

    private fun List<CohortDescription>.downloadCohorts(): List<GetCohortResponse> =
        downloadCohorts(this)

    internal fun downloadCohorts(cohortDescriptions: List<CohortDescription>): List<GetCohortResponse> {
        Logger.d("Downloading cohorts.")
        // Make a request to download each cohort
        return cohortDescriptions.map { description ->
            Logger.d("Downloading cohort ${description.id}")
            cohortApi.getCohort(GetCohortRequest(cohortId = description.id))
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
                Logger.d("Downloaded cohorts")
            }
    }

    private fun List<GetCohortResponse>.storeCohorts() =
        storeCohorts(this)

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
