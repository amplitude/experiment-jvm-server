package com.amplitude.experiment.cohort

/*
 * Based on the Behavioral Cohort API:
 * https://www.docs.developers.amplitude.com/analytics/apis/behavioral-cohorts-api/
 */

internal data class CohortDescription(
    val lastComputed: Long,
    val published: Boolean,
    val archived: Boolean,
    val appId: String,
    val lastMod: Long,
    val type: String,
    val id: String,
    val size: Int,
)

internal object GetCohortsRequest

internal data class GetCohortsResponse(
    val cohorts: List<CohortDescription>,
)

internal data class GetCohortRequest(
    val id: String,
    val props: Boolean,
    val propKeys: List<String>
)

internal data class GetCohortResponse(
    val requestId: String,
    val cohortId: String,
)

internal data class GetCohortStatusRequest(
    val requestId: String,
)

internal data class GetCohortStatusResponse(
    val requestId: String,
    val cohortId: String,
    val asyncStatus: String,
)

internal data class DownloadCohortRequest(
    val requestId: String,
)

internal typealias DownloadCohortResponse = List<Any> // TODO What is the format?

internal interface CohortApi {
    fun getCohorts(request: GetCohortsRequest): GetCohortsResponse
    fun getCohort(request: GetCohortRequest): GetCohortResponse
    fun getCohortStatus(request: GetCohortStatusRequest): GetCohortStatusResponse
    fun downloadCohort(request: DownloadCohortRequest): DownloadCohortResponse
}
