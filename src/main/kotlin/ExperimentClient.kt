package com.amplitude.experiment

import com.amplitude.experiment.util.BackoffConfig
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.backoff
import com.amplitude.experiment.util.toJson
import com.amplitude.experiment.util.toVariant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ExperimentClient internal constructor(
    private val apiKey: String,
    private val config: ExperimentConfig,
    private val httpClient: OkHttpClient,
) {

    private val retry: Boolean = config.fetchRetries > 0
    private val serverUrl: HttpUrl = config.serverUrl.toHttpUrl()
    private val backoffConfig = BackoffConfig(
        attempts = config.fetchRetries,
        min = config.fetchRetryBackoffMinMillis,
        max = config.fetchRetryBackoffMaxMillis,
        scalar = config.fetchRetryBackoffScalar,
    )

    fun fetch(user: ExperimentUser): CompletableFuture<Map<String, Variant>> {
        return doFetch(user, config.fetchTimeoutMillis).handle { variants, t ->
            if (t != null || variants == null) {
                if (retry) {
                    backoff(backoffConfig) {
                        doFetch(user, config.fetchRetryTimeoutMillis)
                    }
                } else {
                    CompletableFuture.failedFuture(t)
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
        Logger.d("Fetch variants for user: $user")
        // Build request to fetch variants for the user
        val body = user.toJson()
            .toByteArray(Charsets.UTF_8)
            .toRequestBody("application/json".toMediaType())
        val url = serverUrl.newBuilder()
            .addPathSegments("sdk/vardata")
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
                    val variants = parseResponse(response)
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

    @Throws(IOException::class)
    private fun parseResponse(response: Response): Map<String, Variant> = response.use {
        if (!response.isSuccessful) {
            throw IOException("fetch error response: $response")
        }
        val body = response.body?.string() ?: ""
        val json = JSONObject(body)
        val variants = mutableMapOf<String, Variant>()
        json.keys().forEach { key ->
            val variant = json.getJSONObject(key).toVariant()
            if (variant != null) {
                variants[key] = variant
            }
        }
        return variants
    }
}
