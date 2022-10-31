package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.request
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/*
 * Based on the Behavioral Cohort API:
 * https://www.docs.developers.amplitude.com/analytics/apis/behavioral-cohorts-api/
 */

internal const val DEFAULT_COHORT_SYNC_URL = "https://cohort.lab.amplitude.com/"

@Serializable
internal data class CohortDescription(
    @SerialName("lastComputed") val lastComputed: Long,
    @SerialName("published") val published: Boolean,
    @SerialName("archived") val archived: Boolean,
    @SerialName("appId") val appId: Int,
    @SerialName("lastMod") val lastMod: Long,
    @SerialName("type") val type: String,
    @SerialName("id") val id: String,
    @SerialName("size") val size: Int,
)

internal object GetCohortsRequest

@Serializable
internal data class GetCohortsResponse(
    @SerialName("cohorts") val cohorts: List<CohortDescription>,
)

internal data class GetCohortRequest(
    val cohortId: String,
)

@Serializable
internal data class GetCohortResponse(
    @SerialName("cohort") val cohort: CohortDescription,
    @SerialName("user_ids") val userIds: List<String?>,
)

internal interface CohortApi {
    fun getCohorts(request: GetCohortsRequest): CompletableFuture<GetCohortsResponse>
    fun getCohort(request: GetCohortRequest): CompletableFuture<GetCohortResponse>
}

internal class CohortApiImpl(
    private val apiKey: String,
    private val secretKey: String,
    private val serverUrl: HttpUrl,
    httpClient: OkHttpClient,
) : CohortApi {

    private val httpClient: OkHttpClient
    private val semaphore = Semaphore(5, true)

    init {
        this.httpClient = httpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    override fun getCohorts(request: GetCohortsRequest): CompletableFuture<GetCohortsResponse> {
        return semaphore.limit {
            get("api/3/cohorts")
        }
    }

    override fun getCohort(request: GetCohortRequest): CompletableFuture<GetCohortResponse> {
        return semaphore.limit { get("api/3/cohorts/${request.cohortId}") }
    }

    private inline fun <reified T> get(path: String): CompletableFuture<T> {
        val url = serverUrl.newBuilder()
            .addPathSegments(path)
            .build()
        val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))
        val request = Request.Builder()
            .get()
            .url(url)
            .addHeader("Authorization", "Basic $basicAuth")
            .build()
        return httpClient.request(request)
    }
}

private inline fun <reified T> Semaphore.limit(block: () -> CompletableFuture<T>): CompletableFuture<T> {
    acquire()
    val result: CompletableFuture<T> = block.invoke().whenComplete { _, _ ->
        release()
    }
    return result
}
