package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okio.IOException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.lang.Thread.sleep
import java.util.Base64
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/*
 * Based on the Behavioral Cohort API:
 * https://www.docs.developers.amplitude.com/analytics/apis/behavioral-cohorts-api/
 */

private const val CDN_COHORT_SYNC_URL = "https://cohort.lab.amplitude.com/"
private const val DIRECT_COHORT_SYNC_URL = "https://amplitude.com/"

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
    fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription>
    fun getCohortMembers(cohortDescription: CohortDescription): Set<String>
}

internal class DirectCohortDownloadApiV3(
    apiKey: String,
    secretKey: String,
    httpClient: OkHttpClient,
) : CohortDownloadApi {

    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val serverUrl = CDN_COHORT_SYNC_URL.toHttpUrl()
    private val semaphore = Semaphore(5, true)
    private val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))

    override fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription> {
        return semaphore.limit {
            val response = httpClient.get<GetCohortDescriptionsResponse>(
                serverUrl = serverUrl,
                path = "api/3/cohorts",
                headers = mapOf("Authorization" to "Basic $basicAuth"),
                queries = mapOf("cohortIds" to cohortIds.sorted().joinToString()),

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

@Serializable
data class GetCohortAsyncResponse(
    @SerialName("cohort_id")
    val cohortId: String,
    @SerialName("request_id")
    val requestId: String,
)

internal class DirectCohortDownloadApiV5(
    apiKey: String,
    secretKey: String,
    httpClient: OkHttpClient,
) : CohortDownloadApi {

    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(5, TimeUnit.MINUTES)
        .build()
    private val cdnServerUrl = CDN_COHORT_SYNC_URL.toHttpUrl()
    private val directServerUrl = DIRECT_COHORT_SYNC_URL.toHttpUrl()
    private val semaphore = Semaphore(5, true)
    private val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))
    private val csvFormat = CSVFormat.RFC4180.builder().apply {
        setHeader()
    }.build()

    override fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription> {
        return semaphore.limit {
            val response = httpClient.get<GetCohortDescriptionsResponse>(
                serverUrl = cdnServerUrl,
                path = "api/3/cohorts",
                headers = mapOf("Authorization" to "Basic $basicAuth"),
                queries = mapOf("cohortIds" to cohortIds.sorted().joinToString()),
            )
            response.cohorts.map { CohortDescription(id = it.id, lastComputed = it.lastComputed, size = it.size) }
        }
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return semaphore.limit {
            Logger.d("getCohortMembers: start - $cohortDescription")
            val initialResponse = httpClient.get<GetCohortAsyncResponse>(
                serverUrl = cdnServerUrl,
                path = "api/5/cohorts/request/${cohortDescription.id}",
                headers = mapOf("Authorization" to "Basic $basicAuth"),
                queries = mapOf("lastComputed" to cohortDescription.lastComputed.toString())
            )
            Logger.d("getCohortMembers: requestId=${initialResponse.requestId}")
            // Poll until the cohort is ready for download
            while (true) {
                val statusResponse = httpClient.get(
                    serverUrl = directServerUrl,
                    path = "api/5/cohorts/request-status/${initialResponse.requestId}",
                    headers = mapOf("Authorization" to "Basic $basicAuth"),
                )
                Logger.d("getCohortMembers: status=${statusResponse.code}")
                if (statusResponse.code == 200) {
                    break
                } else if (statusResponse.code != 202) {
                    throw IOException("Cohort status request resulted in error response ${statusResponse.code}")
                }
                sleep(1000)
            }
            val downloadResponse = httpClient.get(
                serverUrl = directServerUrl,
                path = "api/5/cohorts/request/${initialResponse.requestId}/file",
                headers = mapOf("Authorization" to "Basic $basicAuth"),
            )
            downloadResponse.use {
                val csv = CSVParser.parse(downloadResponse.body?.byteStream(), Charsets.UTF_8, csvFormat)
                return csv.map { it.get("user_id") }.filterNot { it.isNullOrEmpty() }.toSet()
                    .also { Logger.d("getCohortMembers: end - resultSize=${it.size}") }
            }
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
