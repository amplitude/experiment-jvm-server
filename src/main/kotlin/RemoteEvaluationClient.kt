package com.amplitude.experiment

import com.amplitude.experiment.evaluation.EvaluationVariant
import com.amplitude.experiment.util.BackoffConfig
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.backoff
import com.amplitude.experiment.util.filterDefaultVariants
import com.amplitude.experiment.util.json
import com.amplitude.experiment.util.toJson
import com.amplitude.experiment.util.toVariant
import com.amplitude.experiment.util.toVariants
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RemoteEvaluationClient internal constructor(
    private val apiKey: String,
    private val config: RemoteEvaluationConfig = RemoteEvaluationConfig(),
) {

    private val httpClient = OkHttpClient()
    private val retry: Boolean = config.fetchRetries > 0
    private val serverUrl: HttpUrl = config.serverUrl.toHttpUrl()
    private val backoffConfig = BackoffConfig(
        attempts = config.fetchRetries,
        min = config.fetchRetryBackoffMinMillis,
        max = config.fetchRetryBackoffMaxMillis,
        scalar = config.fetchRetryBackoffScalar,
    )

    @Deprecated(
        "Use the fetchV2 method. FetchV2 returns variant objects with default values (e.g. null/off) if the user is evaluated, but not assigned a variant.",
        ReplaceWith("fetchV2(user)")
    )
    fun fetch(user: ExperimentUser): CompletableFuture<Map<String, Variant>> {
        return fetchV2(user).thenApply { it.filterDefaultVariants() }
    }

    fun fetchV2(user: ExperimentUser): CompletableFuture<Map<String, Variant>> {
        return doFetch(user, config.fetchTimeoutMillis).handle { variants, t ->
            if (t != null || variants == null) {
                if (retry) {
                    backoff(backoffConfig) {
                        doFetch(user, config.fetchTimeoutMillis)
                    }
                } else {
                    CompletableFuture<Map<String, Variant>>().apply {
                        completeExceptionally(t)
                    }
                }
            } else {
                CompletableFuture.completedFuture(variants)
            }
        }.thenCompose { it }
    }

    private fun doFetch(
        user: ExperimentUser,
        timeoutMillis: Long
    ): CompletableFuture<Map<String, Variant>> {
        if (user.userId == null && user.deviceId == null) {
            Logger.w("user id and device id are null; amplitude may not resolve identity")
        }
        val libraryUser = user.copyToBuilder().library("experiment-jvm-server/$LIBRARY_VERSION").build()
        Logger.d("Fetch variants for user: $libraryUser")
        // Build request to fetch variants for the user
        val body = libraryUser.toJson()
            .toByteArray(Charsets.UTF_8)
            .toRequestBody("application/json".toMediaType())
        val url = serverUrl.newBuilder()
            .addPathSegments("sdk/v2/vardata")
            .addQueryParameter("v", "0")
            .build()
        val request = Request.Builder()
            .post(body)
            .url(url)
            .addHeader("Authorization", "Api-Key $apiKey")
            .build()
        val future = CompletableFuture<Map<String, Variant>>()
        val call = httpClient.newCall(request)
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        // Execute request and handle response
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    Logger.d("Received fetch response: $response")
                    val variants = response.use {
                        if (!response.isSuccessful) {
                            throw IOException("fetch error response: $response")
                        }
                        parseRemoteResponse(response.body?.string() ?: "")
                    }
                    future.complete(variants)
                } catch (e: IOException) {
                    onFailure(call, e)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }
        })
        return future
    }
}

internal fun parseRemoteResponse(jsonString: String): Map<String, Variant> =
    json.decodeFromString<Map<String, EvaluationVariant>>(
        jsonString
    ).toVariants()
