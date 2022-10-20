package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val MAX_COHORT_SIZE = 15_000
private const val SYNC_INTERVAL = 1L
private val SYNC_INTERVAL_UNIT = TimeUnit.HOURS

internal interface CohortService {
    fun start()
    fun stop()
    fun getCohorts(userId: String): Set<String>
}

internal class CohortServiceImpl(
    private val idProvider: () -> Set<String>,
    private val api: CohortApi,
    private val storage: CohortStorage,
): CohortService {

    private val poller = Executors.newSingleThreadScheduledExecutor()

    internal fun refresh() {
        Logger.d("Refreshing cohorts.")
        getCohortDescriptions()
            .filterInvalid()
            .download()
            .store()
    }

    override fun start() {
        refresh()
        poller.scheduleWithFixedDelay({
            refresh()
        }, SYNC_INTERVAL, SYNC_INTERVAL, SYNC_INTERVAL_UNIT)
    }

    override fun stop() {
        poller.shutdown()
    }

    override fun getCohorts(userId: String): Set<String> {
        return storage.getCohortsForUser(userId)
    }

    internal fun getCohortDescriptions(): List<CohortDescription> {
        Logger.d("Getting cohort descriptions.")
        return api.getCohorts(GetCohortsRequest).get().cohorts.apply {
            Logger.d("Got cohort descriptions: $this")
        }
    }

    private fun List<CohortDescription>.filterInvalid(): List<CohortDescription> =
        filterInvalid(this)

    internal fun filterInvalid(cohortDescriptions: List<CohortDescription>): List<CohortDescription> {
        val cohortIds = idProvider.invoke()
        Logger.d("Filtering out invalid cohort descriptions.")
        // Filter out cohorts which are (1) not being targeted (2) too large (3) not updated
        return cohortDescriptions.filter { description ->
            val storageCohortDescription = storage.getCohortDescription(description.id)
            cohortIds.contains(description.id) &&
                description.size < MAX_COHORT_SIZE &&
                description.lastComputed != storageCohortDescription?.lastComputed
        }.apply {
            Logger.d("Cohorts filtered: $this")
        }
    }

    private fun List<CohortDescription>.download(): List<GetCohortResponse> =
        download(this)

    internal fun download(cohortDescriptions: List<CohortDescription>): List<GetCohortResponse> {
        Logger.d("Downloading cohorts.")
        // Make a request to download each cohort
        return cohortDescriptions.map { description ->
            api.getCohort(GetCohortRequest(cohortId = description.id))
        }
        // Handle exceptions and get the response
        .mapNotNull {
            it.handle<GetCohortResponse?> { response, t ->
                if (response == null || t != null) {
                    Logger.e("get cohort request failed", t)
                    null
                } else {
                    response
                }
            }.join()
        }.apply {
            Logger.d("Downloaded cohorts: $this")
        }
    }

    private fun List<GetCohortResponse>.store() =
        store(this)

    internal fun store(getCohortResponses: List<GetCohortResponse>) {
        Logger.d("Storing cohorts.")
        // Store the cohort included in the response
        getCohortResponses.forEach {
            storage.putCohort(it.cohort, it.userIds)
        }.apply {
            Logger.d("Cohorts stored.")
        }
    }
}
