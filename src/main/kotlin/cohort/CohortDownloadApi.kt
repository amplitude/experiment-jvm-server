package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Base64
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/*
 * Based on the Behavioral Cohort API:
 * https://www.docs.developers.amplitude.com/analytics/apis/behavioral-cohorts-api/
 */

private const val DEFAULT_COHORT_SYNC_URL = "https://cohort.lab.amplitude.com/"

@Serializable
private data class SerialCohortDescription(
    @SerialName("lastComputed") val lastComputed: Long,
    @SerialName("published") val published: Boolean,
    @SerialName("archived") val archived: Boolean,
    @SerialName("appId") val appId: Int,
    @SerialName("lastMod") val lastMod: Long,
    @SerialName("type") val type: String,
    @SerialName("id") val id: String,
    @SerialName("size") val size: Int,
)

@Serializable
private data class GetCohortDescriptionsResponse(
    @SerialName("cohorts") val cohorts: List<SerialCohortDescription>,
)

@Serializable
private data class GetCohortMembersResponse(
    @SerialName("cohort") val cohort: SerialCohortDescription,
    @SerialName("user_ids") val userIds: List<String?>,
)

internal interface CohortDownloadApi {
    fun getCohortDescriptions(): List<CohortDescription>
    fun getCohortMembers(cohortDescription: CohortDescription): Set<String>
}

internal class DirectCohortDownloadApi(
    apiKey: String,
    secretKey: String,
    httpClient: OkHttpClient,
) : CohortDownloadApi {

    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val serverUrl = DEFAULT_COHORT_SYNC_URL.toHttpUrl()
    private val semaphore = Semaphore(5, true)
    private val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))

    override fun getCohortDescriptions(): List<CohortDescription> {
        return semaphore.limit {
            val response = httpClient.get<GetCohortDescriptionsResponse>(
                serverUrl = serverUrl,
                path = "api/3/cohorts",
                headers = mapOf("Authorization" to "Basic $basicAuth"),
            )
            response.cohorts.map { CohortDescription(id = it.id, lastComputed = it.lastComputed, size = it.size) }
        }
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return semaphore.limit {
            val response = httpClient.get<GetCohortMembersResponse>(
                serverUrl = serverUrl,
                path = "api/3/cohorts/${cohortDescription.id}",
                headers = mapOf("Authorization" to "Basic $basicAuth"),
                queries = mapOf(
                    "lastComputed" to "${cohortDescription.lastComputed}",
                    "refreshCohort" to "false",
                    "amp_ids" to "false",
                ),
            )
            response.userIds.filterNotNull().toSet()
        }
    }
}

private inline fun <reified T> Semaphore.limit(block: () -> T): T {
    acquire()
    val result: T = try {
        block.invoke()
    } finally {
        release()
    }
    return result
}
