package com.amplitude.experiment

import com.amplitude.experiment.FetchOptions
import com.amplitude.experiment.evaluation.EvaluationVariant
import com.amplitude.experiment.util.BackoffConfig
import com.amplitude.experiment.util.FetchException
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.backoff
import com.amplitude.experiment.util.json
import com.amplitude.experiment.util.toJson
import com.amplitude.experiment.util.toVariant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class RemoteEvaluationClient internal constructor(
    private val apiKey: String,
    private val config: RemoteEvaluationConfig = RemoteEvaluationConfig(),
) {

    private val httpClient = OkHttpClient.Builder().proxy(config.httpProxy).build()
    private val retry: Boolean = config.fetchRetries > 0
    private val serverUrl: HttpUrl = getServerUrl(config)
    private val backoffConfig = BackoffConfig(
        attempts = config.fetchRetries,
        min = config.fetchRetryBackoffMinMillis,
        max = config.fetchRetryBackoffMaxMillis,
        scalar = config.fetchRetryBackoffScalar,
    )

    @JvmOverloads
    fun fetch(user: ExperimentUser, options: FetchOptions? = null): CompletableFuture<Map<String, Variant>> {
        return doFetch(user, config.fetchTimeoutMillis, options).handle { variants, t ->
            if (t != null || variants == null) {
                if (retry && shouldRetryFetch(t)) {
                    backoff(backoffConfig) {
                        doFetch(user, config.fetchTimeoutMillis, options)
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
        timeoutMillis: Long,
        fetchOptions: FetchOptions?
    ): CompletableFuture<Map<String, Variant>> {
        if (user.userId == null && user.deviceId == null) {
            Logger.warn("user id and device id are null; amplitude may not resolve identity")
        }
        val libraryUser = user.copyToBuilder().library("experiment-jvm-server/$LIBRARY_VERSION").build()
        Logger.debug("Fetch variants for user: $libraryUser")
        // Build request to fetch variants for the user
        val encodedUser = Base64.getEncoder().encodeToString(
            libraryUser.toJson().toByteArray(Charsets.UTF_8)
        )
        val url = serverUrl.newBuilder()
            .addPathSegments("sdk/v2/vardata")
            .addQueryParameter("v", "0")
            .build()
        var requestBuilder = Request.Builder()
            .get()
            .url(url)
            .addHeader("Authorization", "Api-Key $apiKey")
            .addHeader("X-Amp-Exp-User", encodedUser)
        if (fetchOptions?.tracksAssignment != null) {
            requestBuilder = requestBuilder.addHeader("X-Amp-Exp-Track", if (fetchOptions.tracksAssignment) "track" else "no-track")
        }
        if (fetchOptions?.tracksExposure != null) {
            requestBuilder = requestBuilder.addHeader("X-Amp-Exp-Exposure-Track", if (fetchOptions.tracksExposure) "track" else "no-track")
        }
        val request = requestBuilder.build()
        val future = CompletableFuture<Map<String, Variant>>()
        val call = httpClient.newCall(request)
        call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        // Execute request and handle response
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    Logger.debug("Received fetch response: $response")
                    val variants = response.use {
                        if (!response.isSuccessful) {
                            throw FetchException(response.code, "fetch error response: $response")
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
    json.decodeFromString<HashMap<String, EvaluationVariant>>(
        jsonString
    ).mapValues { it.value.toVariant() }

private fun shouldRetryFetch(t: Throwable): Boolean {
    if (t is FetchException) {
        return t.statusCode < 400 || t.statusCode >= 500 || t.statusCode == 429
    }
    return true
}

private fun getServerUrl(config: RemoteEvaluationConfig): HttpUrl {
    return if (config.serverUrl == RemoteEvaluationConfig.Defaults.SERVER_URL) {
        when (config.serverZone) {
            ServerZone.US -> US_SERVER_URL.toHttpUrl()
            ServerZone.EU -> EU_SERVER_URL.toHttpUrl()
        }
    } else {
        config.serverUrl.toHttpUrl()
    }
}
