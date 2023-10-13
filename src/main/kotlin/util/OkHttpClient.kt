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

internal data class HttpErrorResponseException(
    val request: Request?,
    val response: Response,
) : IOException("$request - error response: $response")

private fun OkHttpClient.submit(
    request: Request,
): CompletableFuture<Response> {
    val future = CompletableFuture<Response>()
    val call = newCall(request)
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            try {
                if (!response.isSuccessful) {
                    response.close()
                    throw HttpErrorResponseException(request, response)
                }
                future.complete(response)
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

private fun newGet(
    serverUrl: HttpUrl,
    path: String,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
): Request {
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
    return builder.build()
}

internal fun OkHttpClient.get(
    serverUrl: HttpUrl,
    path: String,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
): Response {
    val request = newGet(serverUrl, path, headers, queries)
    return submit(request).thenApply { it.apply{ close() } }.get()
}

internal inline fun <reified T> OkHttpClient.get(
    serverUrl: HttpUrl,
    path: String,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
    crossinline block: (Response) -> T,
): T {
    val request = newGet(serverUrl, path, headers, queries)
    return submit(request).thenApply {
        it.use { response ->
            block(response)
        }
    }.get()
}

internal inline fun <reified T> OkHttpClient.get(
    serverUrl: HttpUrl,
    path: String,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
): T {
    return this.get(serverUrl, path, headers, queries) { response ->
        val body = response.body?.string() ?: throw IOException("null response body")
        json.decodeFromString(body)
    }
}
