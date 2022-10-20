package com.amplitude.experiment.cohort

import com.amplitude.experiment.LIBRARY_VERSION
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture

/*
 * Based on the Behavioral Cohort API:
 * https://www.docs.developers.amplitude.com/analytics/apis/behavioral-cohorts-api/
 */

private val json = Json {
    ignoreUnknownKeys = true
}

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
    private val httpClient: OkHttpClient,
): CohortApi {

    override fun getCohorts(request: GetCohortsRequest): CompletableFuture<GetCohortsResponse> {
        return getRequest("api/3/cohorts") {
            json.decodeFromString(it)
        }
    }

    override fun getCohort(request: GetCohortRequest): CompletableFuture<GetCohortResponse> {
        return getRequest("api/3/cohorts/${request.cohortId}") {
            json.decodeFromString(it)
        }
    }

    private fun <T> getRequest(path: String, deserializer: (String) -> T): CompletableFuture<T> {
        val url = serverUrl.newBuilder()
            .addPathSegments(path)
            .build()
        val basicAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray(Charsets.UTF_8))
        val okRequest = Request.Builder()
            .get()
            .url(url)
            .addHeader("Authorization", "Basic $basicAuth")
            .addHeader("X-Amp-Exp-Library", "experiment-jvm-server/$LIBRARY_VERSION")
            .build()
        val future = CompletableFuture<T>()
        val call = httpClient.newCall(okRequest)
        // Execute request and handle response
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        if (!response.isSuccessful) {
                            throw IOException("$path - error response: $response")
                        }
                        val body = response.body?.string()
                        deserializer.invoke(body ?: throw IOException("$path - null response body"))
                    }
                    future.complete(result)
                } catch (e: Exception) {
                    fail(e)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                fail(e)
            }

            private fun fail(e: Exception) {
                future.completeExceptionally(e)
            }
        })
        return future
    }
}
