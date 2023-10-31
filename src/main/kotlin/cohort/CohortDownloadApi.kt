package com.amplitude.experiment.cohort

import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.HttpErrorResponseException
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
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
// TODO make configurable to support EU datacenter
private const val CDN_COHORT_SYNC_URL = "https://cohort.lab.amplitude.com/"

@Serializable
internal data class SerialCohortInfoResponse(
    @SerialName("cohort_id") val cohortId: String,
    @SerialName("app_id") val appId: Int = 0,
    @SerialName("org_id") val orgId: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("size") val size: Int = Int.MAX_VALUE,
    @SerialName("description") val description: String? = null,
    @SerialName("last_computed") val lastComputed: Long = 0,
)

@Serializable
internal data class GetCohortAsyncResponse(
    @SerialName("cohort_id")
    val cohortId: String,
    @SerialName("request_id")
    val requestId: String,
)

internal interface CohortDownloadApi {
    fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription>
    fun getCohortMembers(cohortDescription: CohortDescription): Set<String>
}

internal class DynamicCohortDownloadApi(
    private val directApi: DirectCohortDownloadApiV5,
    private val proxyApi: ProxyCohortDownloadApi,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
): CohortDownloadApi {
    override fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription> {
        return try {
            proxyApi.getCohortDescriptions(cohortIds)
        } catch (e: Exception) {
            metrics.onCohortDescriptionsFetchOriginFallback(e)
            directApi.getCohortDescriptions(cohortIds)
        }
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return try {
            proxyApi.getCohortMembers(cohortDescription)
        } catch (e: Exception) {
            metrics.onCohortDownloadOriginFallback(e)
            directApi.getCohortMembers(cohortDescription)
        }
    }
}

internal class ProxyCohortDownloadApi(
    private val deploymentKey: String,
    proxyServerUrl: String,
    httpClient: OkHttpClient,
): CohortDownloadApi {
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val proxyServerUrl = proxyServerUrl.toHttpUrl()

    override fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription> {
        val result = mutableListOf<CohortDescription>()
        for (cohortId in cohortIds) {
            result += httpClient.get<CohortDescription>(
                serverUrl = proxyServerUrl,
                path = "sdk/v2/cohorts/$cohortId/description",
                headers = mapOf("Authorization" to "Api-Key $deploymentKey"),
            )
        }
        return result
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return httpClient.get<Set<String>>(
            serverUrl = proxyServerUrl,
            path = "sdk/v2/cohorts/${cohortDescription.id}/description",
            headers = mapOf("Authorization" to "Api-Key $deploymentKey"),
        )
    }
}

internal class DirectCohortDownloadApiV5(
    apiKey: String,
    secretKey: String,
    httpClient: OkHttpClient,
    private val requestStatusDelay: Long = 5000
) : CohortDownloadApi {

    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val cdnServerUrl = CDN_COHORT_SYNC_URL.toHttpUrl()
    private val semaphore = Semaphore(5, true)
    private val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))
    private val csvFormat = CSVFormat.RFC4180.builder().apply {
        setHeader()
    }.build()

    override fun getCohortDescriptions(cohortIds: Set<String>): List<CohortDescription> {
        return semaphore.limit {
            val result = mutableListOf<CohortDescription>()
            for (cohortId in cohortIds) {
                val response = getCohortInfo(cohortId)
                result += CohortDescription(
                    id = response.cohortId,
                    lastComputed = response.lastComputed,
                    size = response.size
                )
            }
            result
        }
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return semaphore.limit {
            Logger.d("getCohortMembers: start - $cohortDescription")
            val initialResponse = getCohortAsyncRequest(cohortDescription)
            Logger.d("getCohortMembers: requestId=${initialResponse.requestId}")
            // Poll until the cohort is ready for download
            var errors = 0
            while (true) {
                try {
                    val statusResponse = getCohortAsyncRequestStatus(initialResponse.requestId)
                    Logger.d("getCohortMembers: status=${statusResponse.code}")
                    if (statusResponse.code == 200) {
                        break
                    } else if (statusResponse.code != 202) {
                        // Handle successful, but unexpected response codes
                        throw HttpErrorResponseException(null, statusResponse)
                    }
                } catch (e: IOException) {
                    // Don't count 429 response towards the errors count
                    if (e !is HttpErrorResponseException || e.response.code != 429) {
                        errors++
                    }
                    Logger.d("getCohortMembers: request-status error $errors - $e")
                    if (errors >= 3) {
                        throw e
                    }
                }
                sleep(requestStatusDelay)
            }
            return getCohortAsyncRequestMembers(initialResponse.requestId)
        }
    }

    internal fun getCohortInfo(cohortId: String): SerialCohortInfoResponse =
        httpClient.get<SerialCohortInfoResponse>(
            serverUrl = cdnServerUrl,
            path = "api/3/cohorts/info/$cohortId",
            headers = mapOf("Authorization" to "Basic $basicAuth"),
        )

    internal fun getCohortAsyncRequest(cohortDescription: CohortDescription): GetCohortAsyncResponse =
        httpClient.get<GetCohortAsyncResponse>(
            serverUrl = cdnServerUrl,
            path = "api/5/cohorts/request/${cohortDescription.id}",
            headers = mapOf("Authorization" to "Basic $basicAuth"),
            queries = mapOf("lastComputed" to cohortDescription.lastComputed.toString())
        )

    internal fun getCohortAsyncRequestStatus(requestId: String): Response =
        httpClient.get(
            serverUrl = cdnServerUrl,
            path = "api/5/cohorts/request-status/${requestId}",
            headers = mapOf("Authorization" to "Basic $basicAuth"),
        )

    internal fun getCohortAsyncRequestMembers(requestId: String): Set<String> =
        httpClient.get(
            serverUrl = cdnServerUrl,
            path = "api/5/cohorts/request/${requestId}/file",
            headers = mapOf("Authorization" to "Basic $basicAuth"),
        ) { response ->
            val csv = CSVParser.parse(response.body?.byteStream(), Charsets.UTF_8, csvFormat)
            csv.map { it.get("user_id") }.filterNot { it.isNullOrEmpty() }.toSet()
                .also { Logger.d("getCohortMembers: end - resultSize=${it.size}") }
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
