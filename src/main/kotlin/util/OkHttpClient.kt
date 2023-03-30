package com.amplitude.experiment.util

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.concurrent.CompletableFuture

private val json = Json {
    ignoreUnknownKeys = true
}

internal inline fun <reified T> OkHttpClient.request(
    request: Request,
): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val call = newCall(request)
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    if (!response.isSuccessful) {
                        throw IOException("$request - error response: $response")
                    }
                    val body = response.body?.string() ?: throw IOException("$request - null response body")
                    json.decodeFromString<T>(body)
                }
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            future.completeExceptionally(e)
        }
    })
    return future
}

internal inline fun <reified T> OkHttpClient.get(
    serverUrl: HttpUrl,
    path: String,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
): T {
    val url = serverUrl.newBuilder().apply {
        addPathSegments(path)
        queries?.forEach {
            addQueryParameter(it.key, it.value)
        }
    }.build()
    val builder = Request.Builder().get().url(url)
    headers?.forEach {
        builder.addHeader(it.key, it.value)
    }
    val request = builder.build()
    return this.request<T>(request).get()
}
