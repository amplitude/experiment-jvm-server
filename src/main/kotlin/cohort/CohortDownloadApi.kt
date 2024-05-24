package com.amplitude.experiment.cohort

import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.HttpErrorResponseException
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.lang.Thread.sleep
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.IllegalStateException

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
    @SerialName("group_type") val groupType: String = USER_GROUP_TYPE,
)

@Serializable
internal data class GetCohortAsyncResponse(
    @SerialName("cohort_id")
    val cohortId: String,
    @SerialName("request_id")
    val requestId: String,
)

internal class CachedCohortDownloadException(
    val members: Set<String>,
    override val cause: Exception,
) : Exception("Initial cohort download failed, but fallback on cache succeeded.")

internal interface CohortDownloadApi {
    fun getCohortDescription(cohortId: String): CohortDescription
    fun getCohortMembers(cohortDescription: CohortDescription): Set<String>
}

internal class DynamicCohortDownloadApi(
    private val directApi: DirectCohortDownloadApiV5,
    private val proxyApi: ProxyCohortDownloadApi,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : CohortDownloadApi {
    override fun getCohortDescription(cohortId: String): CohortDescription {
        return try {
            proxyApi.getCohortDescription(cohortId)
        } catch (e: Exception) {
            metrics.onCohortDescriptionsFetchOriginFallback(e)
            directApi.getCohortDescription(cohortId)
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
) : CohortDownloadApi {
    private val httpClient: OkHttpClient = httpClient.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val proxyServerUrl = proxyServerUrl.toHttpUrl()

    override fun getCohortDescription(cohortId: String): CohortDescription {
        return httpClient.get<CohortDescription>(
            serverUrl = proxyServerUrl,
            path = "sdk/v2/cohorts/$cohortId/description",
            headers = mapOf("Authorization" to "Api-Key $deploymentKey"),
        )
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return httpClient.get<Set<String>>(
            serverUrl = proxyServerUrl,
            path = "sdk/v2/cohorts/${cohortDescription.id}/members",
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

    internal val httpClient: OkHttpClient = httpClient.newBuilder()
        .followRedirects(false)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    internal val cdnServerUrl = CDN_COHORT_SYNC_URL.toHttpUrl()
    internal val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))
    private val csvFormat = CSVFormat.RFC4180.builder().apply {
        setHeader()
    }.build()

    override fun getCohortDescription(cohortId: String): CohortDescription {
        val response = getCohortInfo(cohortId)
        return CohortDescription(
            id = response.cohortId,
            lastComputed = response.lastComputed,
            size = response.size,
            groupType = response.groupType,
        )
    }

    override fun getCohortMembers(cohortDescription: CohortDescription): Set<String> {
        return try {
            Logger.d("getCohortMembers(${cohortDescription.id}): start - $cohortDescription")
            val initialResponse = getCohortAsyncRequest(cohortDescription)
            Logger.d("getCohortMembers(${cohortDescription.id}): requestId=${initialResponse.requestId}")
            // Poll until the cohort is ready for download
            var errors = 0
            while (true) {
                try {
                    val statusResponse = getCohortAsyncRequestStatus(initialResponse.requestId)
                    Logger.d("getCohortMembers(${cohortDescription.id}): status=${statusResponse.code}")
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
                    Logger.d("getCohortMembers(${cohortDescription.id}): request-status error $errors - $e")
                    if (errors >= 3) {
                        throw e
                    }
                }
                sleep(requestStatusDelay)
            }
            val location = getCohortAsyncRequestLocation(initialResponse.requestId)
            getCohortAsyncRequestMembers(cohortDescription.id, cohortDescription.groupType, location)
                .also { Logger.d("getCohortMembers(${cohortDescription.id}): end - resultSize=${it.size}") }
        } catch (e: Exception) {
            try {
                val cachedMembers = getCachedCohortMembers(cohortDescription.id, cohortDescription.groupType)
                    .also { Logger.d("getCohortMembers(${cohortDescription.id}): end cached fallback - resultSize=${it.size}") }
                throw CachedCohortDownloadException(cachedMembers, e)
            } catch (e2: Exception) {
                throw e2
            }
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
            path = "api/5/cohorts/request-status/$requestId",
            headers = mapOf("Authorization" to "Basic $basicAuth"),
        )

    internal fun getCohortAsyncRequestLocation(requestId: String): HttpUrl =
        httpClient.get(
            serverUrl = cdnServerUrl,
            path = "api/5/cohorts/request/$requestId/file",
            headers = mapOf("Authorization" to "Basic $basicAuth"),
        ) { response ->
            val location = response.headers["location"]
                ?: throw IllegalStateException("Cohort response location must not be null")
            location.toHttpUrl()
        }
    internal fun getCohortAsyncRequestMembers(
        cohortId: String,
        groupType: String,
        location: HttpUrl
    ): Set<String> {
        val url = location.newBuilder().host(cdnServerUrl.host).build()
        return httpClient.get(
            serverUrl = url,
            headers = mapOf(
                "X-Amp-Authorization" to "Basic $basicAuth",
                "X-Cohort-ID" to cohortId,
            ),
        ) { response ->
            val inputStream = response.body?.byteStream()
                ?: throw IllegalStateException("Cohort response body must not be null.")
            parseCsvResponse(inputStream, groupType)
        }
    }

    internal fun getCachedCohortMembers(cohortId: String, groupType: String): Set<String> {
        return httpClient.get(
            serverUrl = cdnServerUrl,
            path = "/cohorts",
            headers = mapOf(
                "X-Amp-Authorization" to "Basic $basicAuth",
                "X-Cohort-ID" to cohortId,
            ),
        ) { response ->
            val inputStream = response.body?.byteStream()
                ?: throw IllegalStateException("Cohort response body must not be null.")
            parseCsvResponse(inputStream, groupType)
        }
    }

    internal fun parseCsvResponse(inputStream: InputStream, groupType: String): Set<String> {
        val csv = CSVParser.parse(inputStream, Charsets.UTF_8, csvFormat)
        return if (groupType == USER_GROUP_TYPE) {
            csv.map { it.get("user_id") }.filterNot { it.isNullOrEmpty() }.toSet()
        } else {
            csv.map {
                try {
                    // CSV returned from API has all strings prefixed with a tab character
                    it.get("\tgroup_value")
                } catch (e: IllegalArgumentException) {
                    it.get("group_value")
                }
            }.filterNot {
                it.isNullOrEmpty()
            }.map {
                // CSV returned from API has all strings prefixed with a tab character
                it.removePrefix("\t")
            }.toSet()
        }
    }
}
